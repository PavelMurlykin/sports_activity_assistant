package com.pamurlykin.sportsactivityassistant.data.backup

@ConsistentCopyVisibility
data class ParsedImport internal constructor(
    val document: BackupDocument,
    val format: String,
    val sourceVersion: Int,
    val locations: Map<String, String>,
    val profileLabels: Map<String, String>,
    val csv: Boolean = false,
)

data class ImportChoices(
    val selectedProfile: String? = null,
    val preserveAllProfiles: Boolean = false,
    val centerMappings: Map<String, String?> = emptyMap(),
    val skipTrainingIds: Set<String> = emptySet(),
    val keepLocalIds: Set<String> = emptySet(),
)

data class ImportCenterOption(val publicId: String, val title: String)
data class ImportCenterPreview(val sourceId: String, val title: String, val target: String?, val resolved: Boolean, val fixed: Boolean)
data class ImportProfileOption(val publicId: String, val title: String)
data class ImportRecordPreview(val publicId: String, val title: String, val kind: String, val exists: Boolean, val conflict: Boolean, val possibleMatch: Boolean)
data class ImportPreview(
    val format: String,
    val trainings: Int,
    val routes: Int,
    val plans: Int,
    val rules: Int,
    val choices: ImportChoices,
    val profiles: List<ImportProfileOption> = emptyList(),
    val centers: List<ImportCenterPreview> = emptyList(),
    val centerOptions: List<ImportCenterOption> = emptyList(),
    val records: List<ImportRecordPreview> = emptyList(),
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val canApply: Boolean get() = errors.isEmpty()
}
