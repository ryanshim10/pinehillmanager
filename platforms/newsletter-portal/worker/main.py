import os
import sys
import json
import logging
from datetime import datetime
from typing import List, Optional

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from sqlmodel import Session, select, func

# Add parent directory to path for shared imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from shared.db import make_engine, init_db
from shared.models import (
    User, Source, Keyword, Item, Schedule, Newsletter, SendLog, SendStatus,
    SourceType, KeywordBucket, NewsletterStatus
)
from shared.connectors import get_connector, ConnectorError
from shared.llm import NewsletterGenerator
from shared.mail import get_mailer

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Configuration
DATABASE_URL = os.environ["DATABASE_URL"]
WORKER_TIMEZONE = os.environ.get("WORKER_TIMEZONE", "Asia/Seoul")
WORKER_TICK_SECONDS = int(os.environ.get("WORKER_TICK_SECONDS", "300"))

# Database engine
engine = make_engine(DATABASE_URL)

# Scheduler
scheduler = BackgroundScheduler(timezone=WORKER_TIMEZONE)


def fetch_source(source_id: int):
    """Fetch items from a single source"""
    with Session(engine) as session:
        source = session.get(Source, source_id)
        if not source or not source.enabled:
            return
        
        try:
            logger.info(f"Fetching source {source.id}: {source.name} ({source.type})")
            
            # Parse config
            config = {}
            if source.config:
                config = json.loads(source.config)
            
            # Get connector
            connector = get_connector(source.type, source.id, source.url, config)
            
            # Fetch items
            items = connector.fetch()
            
            # Save items
            added_count = 0
            for item in items:
                # Check for duplicates
                existing = session.exec(
                    select(Item).where(Item.content_hash == item.content_hash)
                ).first()
                
                if existing:
                    continue
                
                # Create new item
                db_item = Item(
                    source_id=source.id,
                    user_id=source.user_id,
                    title=item.title,
                    url=item.url,
                    author=item.author,
                    published_at=item.published_at,
                    content_text=item.content_text,
                    content_html=item.content_html,
                    content_hash=item.content_hash,
                    is_processed=False
                )
                
                # Calculate relevance score based on keywords
                db_item.relevance_score = calculate_relevance(session, source.user_id, item)
                
                session.add(db_item)
                added_count += 1
            
            # Update source
            source.last_run_at = datetime.utcnow()
            source.last_status = "ok"
            source.last_error = None
            source.fetch_count += added_count
            source.updated_at = datetime.utcnow()
            session.add(source)
            session.commit()
            
            logger.info(f"Source {source.id}: added {added_count} items")
            
        except Exception as e:
            logger.error(f"Source {source.id} fetch error: {e}")
            
            source.last_run_at = datetime.utcnow()
            source.last_status = "error"
            source.last_error = str(e)[:500]
            source.updated_at = datetime.utcnow()
            session.add(source)
            session.commit()


def calculate_relevance(session: Session, user_id: int, item) -> float:
    """Calculate relevance score based on user keywords"""
    score = 0.0
    
    # Get user keywords
    keywords = session.exec(
        select(Keyword).where(Keyword.user_id == user_id)
    ).all()
    
    content = f"{item.title} {item.content_text}".lower()
    
    for kw in keywords:
        if kw.text.lower() in content:
            multiplier = {
                KeywordBucket.TOP: 10.0,
                KeywordBucket.IMPORTANT: 5.0,
                KeywordBucket.NORMAL: 1.0,
                KeywordBucket.EXCLUDE: -100.0
            }.get(kw.bucket, 1.0)
            
            score += kw.weight * multiplier
    
    return max(0.0, score)


def process_all_sources():
    """Process all enabled sources"""
    logger.info("Starting source processing job")
    
    with Session(engine) as session:
        sources = session.exec(
            select(Source).where(Source.enabled == True)
        ).all()
        
        for source in sources:
            try:
                fetch_source(source.id)
            except Exception as e:
                logger.error(f"Error processing source {source.id}: {e}")
    
    logger.info("Source processing job completed")


def generate_newsletter_for_schedule(schedule_id: int):
    """Generate and send newsletter for a schedule"""
    with Session(engine) as session:
        schedule = session.get(Schedule, schedule_id)
        if not schedule or not schedule.enabled:
            return
        
        logger.info(f"Processing schedule {schedule.id}: {schedule.name}")
        
        try:
            # Get recent unprocessed items with high relevance
            items = session.exec(
                select(Item)
                .where(Item.user_id == schedule.user_id)
                .where(Item.is_processed == False)
                .order_by(Item.relevance_score.desc())
                .limit(schedule.max_items)
            ).all()
            
            if not items:
                logger.info(f"Schedule {schedule.id}: no items to process")
                return
            
            # Prepare items for generation
            item_data = [
                {
                    "id": item.id,
                    "title": item.title,
                    "url": item.url,
                    "content_text": item.content_text,
                    "published_at": item.published_at
                }
                for item in items
            ]
            
            # Generate newsletter
            generator = NewsletterGenerator()
            result = generator.generate(item_data, schedule.subject_template)
            
            # Create newsletter
            newsletter = Newsletter(
                user_id=schedule.user_id,
                status=NewsletterStatus.RECOMMENDED,
                subject=result["subject"],
                html=result["html"],
                text=result["text"],
                item_count=len(items),
                generated_by="ai"
            )
            session.add(newsletter)
            session.commit()
            session.refresh(newsletter)
            
            # Link items
            from shared.models import NewsletterItem
            for order, item in enumerate(items):
                link = NewsletterItem(
                    newsletter_id=newsletter.id,
                    item_id=item.id,
                    order=order
                )
                session.add(link)
                
                # Mark item as processed
                item.is_processed = True
                session.add(item)
            
            session.commit()
            
            # Send emails
            recipients = [e.strip() for e in schedule.recipient_emails.split(",") if e.strip()]
            mailer = get_mailer()
            
            sent_count = 0
            error_count = 0
            
            for email in recipients:
                log = SendLog(
                    newsletter_id=newsletter.id,
                    recipient_email=email,
                    status=SendStatus.PENDING
                )
                session.add(log)
                session.commit()
                
                result = mailer.send_newsletter(
                    to_email=email,
                    subject=newsletter.subject,
                    html_content=newsletter.html,
                    text_content=newsletter.text
                )
                
                if result["success"]:
                    log.status = SendStatus.OK
                    log.sent_at = datetime.utcnow()
                    sent_count += 1
                else:
                    log.status = SendStatus.ERROR
                    log.error = result["error"]
                    error_count += 1
                
                session.add(log)
                session.commit()
            
            # Update newsletter status
            newsletter.status = NewsletterStatus.SENT
            newsletter.sent_at = datetime.utcnow()
            newsletter.sent_count = sent_count
            newsletter.error_count = error_count
            session.add(newsletter)
            
            # Update schedule
            schedule.last_run_at = datetime.utcnow()
            session.add(schedule)
            session.commit()
            
            logger.info(f"Schedule {schedule.id}: sent to {sent_count} recipients, {error_count} errors")
            
        except Exception as e:
            logger.error(f"Schedule {schedule.id} error: {e}")
            raise


def check_schedules():
    """Check and run due schedules"""
    logger.info("Checking schedules")
    
    with Session(engine) as session:
        schedules = session.exec(
            select(Schedule).where(Schedule.enabled == True)
        ).all()
        
        now = datetime.utcnow()
        
        for schedule in schedules:
            try:
                # Parse cron expression
                trigger = CronTrigger.from_crontab(schedule.cron_expression)
                
                # Check if due
                next_run = trigger.get_next_fire_time(None, now)
                
                # If last_run_at is None or next_run is in the past, run it
                if schedule.last_run_at is None:
                    should_run = True
                else:
                    # Check if we're past the next scheduled time
                    last_next = trigger.get_next_fire_time(None, schedule.last_run_at)
                    should_run = last_next and last_next <= now
                
                if should_run:
                    logger.info(f"Schedule {schedule.id} is due, running...")
                    generate_newsletter_for_schedule(schedule.id)
                    
            except Exception as e:
                logger.error(f"Error checking schedule {schedule.id}: {e}")
    
    logger.info("Schedule check completed")


def tick():
    """Main worker tick - runs all jobs"""
    logger.info("Worker tick started")
    
    try:
        process_all_sources()
    except Exception as e:
        logger.error(f"Source processing error: {e}")
    
    try:
        check_schedules()
    except Exception as e:
        logger.error(f"Schedule check error: {e}")
    
    logger.info("Worker tick completed")


def init_scheduler():
    """Initialize APScheduler with jobs"""
    # Add tick job
    scheduler.add_job(
        tick,
        'interval',
        seconds=WORKER_TICK_SECONDS,
        id='worker_tick',
        replace_existing=True
    )
    
    logger.info(f"Scheduler initialized with tick interval: {WORKER_TICK_SECONDS}s")


def main():
    """Main worker loop"""
    logger.info("Worker starting...")
    
    # Initialize database
    init_db(engine)
    logger.info("Database initialized")
    
    # Initialize scheduler
    init_scheduler()
    scheduler.start()
    logger.info("Scheduler started")
    
    try:
        # Keep running
        while True:
            import time
            time.sleep(1)
    except (KeyboardInterrupt, SystemExit):
        logger.info("Shutting down...")
        scheduler.shutdown()


if __name__ == "__main__":
    main()
