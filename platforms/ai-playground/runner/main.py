import os
import subprocess
import signal
import psutil
import shutil
import tempfile
from datetime import datetime, timedelta
from typing import Optional, Dict, List
from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel

# Configuration
PORT_START = int(os.getenv("PORT_START", "9000"))
PORT_END = int(os.getenv("PORT_END", "9100"))
MAX_APPS = int(os.getenv("MAX_APPS", "50"))
IDLE_TIMEOUT = int(os.getenv("IDLE_TIMEOUT", "3600"))  # seconds
UPLOADS_DIR = Path(os.getenv("UPLOADS_DIR", "/app/uploads"))

# Global state
running_apps: Dict[str, dict] = {}
port_pool: List[int] = list(range(PORT_START, PORT_END + 1))
available_ports: set = set(port_pool)


class RunRequest(BaseModel):
    app_id: str
    app_path: str  # Relative path in uploads dir


class RunResponse(BaseModel):
    app_id: str
    port: int
    url: str
    status: str
    started_at: datetime


class StatusResponse(BaseModel):
    app_id: str
    status: str
    port: Optional[int]
    url: Optional[str]
    pid: Optional[int]
    started_at: Optional[datetime]
    last_accessed: Optional[datetime]


class StopRequest(BaseModel):
    app_id: str


def get_app_dir(app_id: str) -> Path:
    """Get the directory where app files are stored."""
    return UPLOADS_DIR / app_id


def find_free_port() -> Optional[int]:
    """Find an available port from the pool."""
    for port in list(available_ports):
        # Check if port is actually free
        import socket
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            if s.connect_ex(('0.0.0.0', port)) != 0:
                return port
            # Port is in use, remove from available
            available_ports.discard(port)
    return None


def release_port(port: int):
    """Release a port back to the pool."""
    if PORT_START <= port <= PORT_END:
        available_ports.add(port)


def start_streamlit_app(app_id: str, app_path: str, port: int) -> int:
    """Start a streamlit app subprocess."""
    app_dir = get_app_dir(app_id)
    app_file = app_dir / app_path
    
    if not app_file.exists():
        # Try finding app.py in the directory
        app_file = app_dir / "app.py"
        if not app_file.exists():
            raise HTTPException(status_code=404, detail=f"App file not found: {app_path}")
    
    # Create a temporary config for this instance
    temp_dir = Path(tempfile.mkdtemp(prefix=f"streamlit_{app_id}_"))
    
    # Prepare command
    cmd = [
        "streamlit", "run", str(app_file),
        "--server.port", str(port),
        "--server.address", "0.0.0.0",
        "--server.headless", "true",
        "--server.enableCORS", "false",
        "--server.enableXsrfProtection", "false",
        "--browser.gatherUsageStats", "false",
        "--server.baseUrlPath", f"/app/{app_id}",
    ]
    
    # Set up environment
    env = os.environ.copy()
    env["STREAMLIT_SERVER_PORT"] = str(port)
    env["STREAMLIT_BROWSER_GATHER_USAGE_STATS"] = "false"
    
    # Start process
    process = subprocess.Popen(
        cmd,
        cwd=str(app_dir),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        preexec_fn=os.setsid if hasattr(os, 'setsid') else None
    )
    
    return process.pid


def stop_process(pid: int):
    """Stop a process by PID."""
    try:
        if hasattr(os, 'killpg'):
            os.killpg(os.getpgid(pid), signal.SIGTERM)
        else:
            os.kill(pid, signal.SIGTERM)
        
        # Wait a bit then force kill if still running
        import time
        time.sleep(2)
        
        try:
            if hasattr(os, 'killpg'):
                os.killpg(os.getpgid(pid), signal.SIGKILL)
            else:
                os.kill(pid, signal.SIGKILL)
        except (ProcessLookupError, OSError):
            pass  # Already dead
    except (ProcessLookupError, OSError):
        pass  # Already dead


def cleanup_idle_apps():
    """Stop apps that have been idle for too long."""
    now = datetime.utcnow()
    to_stop = []
    
    for app_id, app_info in list(running_apps.items()):
        last_accessed = app_info.get("last_accessed", app_info["started_at"])
        if (now - last_accessed).total_seconds() > IDLE_TIMEOUT:
            to_stop.append(app_id)
    
    for app_id in to_stop:
        stop_app_internal(app_id)


def stop_app_internal(app_id: str) -> bool:
    """Internal function to stop an app."""
    if app_id not in running_apps:
        return False
    
    app_info = running_apps[app_id]
    pid = app_info.get("pid")
    port = app_info.get("port")
    
    if pid:
        stop_process(pid)
    
    if port:
        release_port(port)
    
    del running_apps[app_id]
    return True


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events."""
    # Startup
    UPLOADS_DIR.mkdir(parents=True, exist_ok=True)
    yield
    # Shutdown - cleanup all running apps
    for app_id in list(running_apps.keys()):
        stop_app_internal(app_id)


app = FastAPI(title="AI Playground Runner", version="0.1.0", lifespan=lifespan)


@app.get("/health")
def health():
    return {
        "ok": True,
        "running_apps": len(running_apps),
        "available_ports": len(available_ports),
        "total_ports": len(port_pool)
    }


@app.post("/run", response_model=RunResponse)
def run_app(req: RunRequest, background_tasks: BackgroundTasks):
    """Start running a streamlit app."""
    # Check if already running
    if req.app_id in running_apps:
        app_info = running_apps[req.app_id]
        app_info["last_accessed"] = datetime.utcnow()
        return RunResponse(
            app_id=req.app_id,
            port=app_info["port"],
            url=f"http://localhost:{app_info['port']}/app/{req.app_id}",
            status="running",
            started_at=app_info["started_at"]
        )
    
    # Check if we have capacity
    if len(running_apps) >= MAX_APPS:
        # Try to cleanup idle apps first
        cleanup_idle_apps()
        if len(running_apps) >= MAX_APPS:
            raise HTTPException(status_code=503, detail="Maximum number of running apps reached")
    
    # Find available port
    port = find_free_port()
    if port is None:
        raise HTTPException(status_code=503, detail="No available ports")
    
    available_ports.discard(port)
    
    try:
        # Start the app
        pid = start_streamlit_app(req.app_id, req.app_path, port)
        
        now = datetime.utcnow()
        running_apps[req.app_id] = {
            "pid": pid,
            "port": port,
            "app_path": req.app_path,
            "started_at": now,
            "last_accessed": now
        }
        
        return RunResponse(
            app_id=req.app_id,
            port=port,
            url=f"http://localhost:{port}/app/{req.app_id}",
            status="starting",
            started_at=now
        )
    except Exception as e:
        release_port(port)
        raise HTTPException(status_code=500, detail=f"Failed to start app: {str(e)}")


@app.post("/stop")
def stop_app(req: StopRequest):
    """Stop a running streamlit app."""
    if not stop_app_internal(req.app_id):
        raise HTTPException(status_code=404, detail="App not found or not running")
    return {"ok": True, "message": f"App {req.app_id} stopped"}


@app.get("/status/{app_id}", response_model=StatusResponse)
def get_status(app_id: str):
    """Get the status of a running app."""
    if app_id not in running_apps:
        return StatusResponse(
            app_id=app_id,
            status="stopped",
            port=None,
            url=None,
            pid=None,
            started_at=None,
            last_accessed=None
        )
    
    app_info = running_apps[app_id]
    
    # Check if process is still alive
    pid = app_info.get("pid")
    is_running = False
    if pid:
        try:
            process = psutil.Process(pid)
            is_running = process.is_running() and process.status() != psutil.STATUS_ZOMBIE
        except psutil.NoSuchProcess:
            is_running = False
    
    if not is_running:
        # Process died, clean up
        stop_app_internal(app_id)
        return StatusResponse(
            app_id=app_id,
            status="stopped",
            port=None,
            url=None,
            pid=None,
            started_at=None,
            last_accessed=None
        )
    
    # Update last accessed
    app_info["last_accessed"] = datetime.utcnow()
    
    return StatusResponse(
        app_id=app_id,
        status="running",
        port=app_info["port"],
        url=f"http://localhost:{app_info['port']}/app/{app_id}",
        pid=pid,
        started_at=app_info["started_at"],
        last_accessed=app_info["last_accessed"]
    )


@app.get("/apps", response_model=List[StatusResponse])
def list_apps():
    """List all running apps."""
    result = []
    for app_id in list(running_apps.keys()):
        try:
            status = get_status(app_id)
            result.append(status)
        except Exception:
            pass
    return result


@app.post("/cleanup")
def trigger_cleanup():
    """Manually trigger cleanup of idle apps."""
    cleanup_idle_apps()
    return {"ok": True, "running_apps": len(running_apps)}
