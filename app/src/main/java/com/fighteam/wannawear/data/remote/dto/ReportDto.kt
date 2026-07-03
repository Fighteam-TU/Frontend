package com.fighteam.wannawear.data.remote.dto

// ⚠️ 아직 백엔드에 대응 엔드포인트가 없음 (2026-07-03 기준 /v3/api-docs에서 report 관련 경로 없음 확인).
// 프론트 UI/로컬 상태는 미리 준비해두고, 백엔드가 POST /api/exchanges/{exchangeId}/report를
// 만들어주면 바로 연결되는 구조. 요청 바디 필드명은 백엔드와 협의 필요 — 우선 이 형태로 제안.
data class ReportRequest(
    val reason: String,       // NO_SHIP | FAKE_OR_DAMAGED | RUDE_BEHAVIOR | SCAM_SUSPECTED | OTHER
    val detail: String? = null
)
