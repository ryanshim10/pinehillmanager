package com.ryan.pinehill.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pinehill.data.model.Unit
import com.ryan.pinehill.data.model.UnitStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(
    viewModel: UnitsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onUnitClick: (Unit) -> Unit = {}
) {
    val units by viewModel.units.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<UnitStatus?>(null) }
    
    val filteredUnits = units.filter { unit ->
        val matchesSearch = searchQuery.isEmpty() || 
            unit.unitId.contains(searchQuery, ignoreCase = true) ||
            unit.roomNo.toString().contains(searchQuery)
        val matchesStatus = selectedStatus == null || unit.status == selectedStatus
        matchesSearch && matchesStatus
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("세대 현황") },
                actions = {
                    IconButton(onClick = { /* 검색 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "검색")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* 추가 */ }) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 통계 카드
            UnitStatsCard(units = units)
            
            // 필터 칩
            StatusFilterChips(
                selectedStatus = selectedStatus,
                onStatusSelected = { selectedStatus = it }
            )
            
            // 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("호실 검색 (예: 201)") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            
            // 세대 리스트
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredUnits) { unit ->
                    UnitCard(
                        unit = unit,
                        onClick = { onUnitClick(unit) }
                    )
                }
            }
        }
    }
}

@Composable
fun UnitStatsCard(units: List<Unit>) {
    val total = units.size
    val rented = units.count { it.status == UnitStatus.RENTED }
    val vacant = units.count { it.status == UnitStatus.VACANT }
    val maintenance = units.count { it.status == UnitStatus.MAINTENANCE }
    val lawsuit = units.count { it.status == UnitStatus.LAWSUIT }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "총 $total 세대",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("임대중", rented, Color(0xFF4CAF50))
                StatItem("공실", vacant, Color(0xFF9E9E9E))
                StatItem("정비중", maintenance, Color(0xFFFF9800))
                StatItem("소송", lawsuit, Color(0xFFF44336))
            }
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
fun StatusFilterChips(
    selectedStatus: UnitStatus?,
    onStatusSelected: (UnitStatus?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onStatusSelected(null) },
            label = { Text("전체") }
        )
        FilterChip(
            selected = selectedStatus == UnitStatus.RENTED,
            onClick = { onStatusSelected(UnitStatus.RENTED) },
            label = { Text("임대중") }
        )
        FilterChip(
            selected = selectedStatus == UnitStatus.MAINTENANCE,
            onClick = { onStatusSelected(UnitStatus.MAINTENANCE) },
            label = { Text("정비중") }
        )
        FilterChip(
            selected = selectedStatus == UnitStatus.LAWSUIT,
            onClick = { onStatusSelected(UnitStatus.LAWSUIT) },
            label = { Text("소송") }
        )
    }
}

@Composable
fun UnitCard(
    unit: Unit,
    onClick: () -> Unit
) {
    val statusColor = when (unit.status) {
        UnitStatus.RENTED -> Color(0xFF4CAF50)
        UnitStatus.VACANT -> Color(0xFF9E9E9E)
        UnitStatus.MAINTENANCE -> Color(0xFFFF9800)
        UnitStatus.LAWSUIT -> Color(0xFFF44336)
        UnitStatus.OTHER -> Color(0xFF607D8B)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 호실 번호
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unit.roomNo.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unit.unitId,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${unit.floor}층 ${unit.roomType ?: ""}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unit.targetPrice != null) {
                    Text(
                        text = unit.targetPrice,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 상태 뱃지
            Box(
                modifier = Modifier
                    .background(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = unit.getStatusText(),
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
