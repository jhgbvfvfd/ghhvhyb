package com.example.kpzi.engine.loader

import com.example.kpzi.domain.model.InstalledMod

enum class ConflictType(val labelTh: String) {
    DEPENDENCY_LOOP("วนลูปการพึ่งพา (Loop)"),
    VERSION_MISMATCH("เวอร์ชันไม่ตรงตามกำหนด"),
    SMAPI_VERSION_MISMATCH("เวอร์ชัน SMAPI ไม่เพียงพอ"),
    MISSING_DEPENDENCY("ขาดมอดที่จำเป็นต้องใช้"),
    DISABLED_DEPENDENCY("มอดหลักถูกปิดใช้งาน"),
    DUPLICATE_UNIQUE_ID("UniqueID ซ้ำซ้อน"),
}

enum class ConflictSeverity(val labelTh: String) {
    CRITICAL("วิกฤต"),
    WARNING("คำเตือน"),
}

data class ModConflictIssue(
    val id: String,
    val type: ConflictType,
    val severity: ConflictSeverity,
    val primaryModUniqueId: String,
    val primaryModName: String,
    val relatedModUniqueIds: List<String> = emptyList(),
    val requiredVersion: String? = null,
    val actualVersion: String? = null,
    val summaryTh: String,
    val detailTh: String,
)

data class ConflictDetectionReport(
    val scannedModCount: Int,
    val enabledModCount: Int,
    val loopCount: Int,
    val versionMismatchCount: Int,
    val missingDependencyCount: Int,
    val disabledDependencyCount: Int,
    val duplicateIdCount: Int,
    val issues: List<ModConflictIssue>,
    val scannedAtMs: Long = System.currentTimeMillis(),
) {
    val hasIssues: Boolean get() = issues.isNotEmpty()
    val criticalCount: Int get() = issues.count { it.severity == ConflictSeverity.CRITICAL }
}

/**
 * Clean-room Conflict Detection Engine:
 * Cross-references manifest.json requirements across all installed mods stored in Room
 * to detect circular dependency loops, dependency version mismatches, SMAPI version
 * incompatibilities, missing/disabled host dependencies, and duplicate UniqueIDs.
 */
class ConflictDetector(
    private val compatibilityEngine: CompatibilityEngine = CompatibilityEngine(),
) {

    fun analyze(
        installedMods: List<InstalledMod>,
        smapiVersion: String?,
    ): ConflictDetectionReport {
        val issues = mutableListOf<ModConflictIssue>()

        // Index mods by lowercase UniqueID
        val groupedById = installedMods
            .filter { it.uniqueId.isNotBlank() }
            .groupBy { it.uniqueId.lowercase() }

        // 1. Duplicate UniqueID detection
        groupedById.forEach { (_, group) ->
            if (group.size > 1) {
                val first = group.first()
                val folderNames = group.joinToString(", ") { it.folderName }
                issues.add(
                    ModConflictIssue(
                        id = "dup:${first.uniqueId.lowercase()}",
                        type = ConflictType.DUPLICATE_UNIQUE_ID,
                        severity = ConflictSeverity.CRITICAL,
                        primaryModUniqueId = first.uniqueId,
                        primaryModName = first.manifest.name,
                        relatedModUniqueIds = group.map { it.folderName },
                        summaryTh = "พบ UniqueID ซ้ำกัน ${group.size} โฟลเดอร์",
                        detailTh = "UniqueID '${first.uniqueId}' ปรากฏซ้ำในโฟลเดอร์: $folderNames กรุณาลบชุดที่ซ้ำออกเพื่อไม่ให้ SMAPI ข้ามการโหลด",
                    ),
                )
            }
        }

        val modById = groupedById.mapValues { (_, list) ->
            list.firstOrNull { it.isEnabled } ?: list.first()
        }

        // 2. Build directed dependency graph & cross-reference manifest requirements
        val graph = mutableMapOf<String, MutableList<String>>()

        for (mod in modById.values) {
            val sourceKey = mod.uniqueId.lowercase()
            val edges = graph.getOrPut(sourceKey) { mutableListOf() }

            // 2a. Check MinimumApiVersion against target/detected SMAPI version
            val minApi = mod.manifest.minimumApiVersion
            if (!minApi.isNullOrBlank() && !smapiVersion.isNullOrBlank()) {
                val cleanSmapi = smapiVersion.substringBefore("-")
                if (compatibilityEngine.compareVersions(cleanSmapi, minApi) < 0) {
                    issues.add(
                        ModConflictIssue(
                            id = "smapi:$sourceKey:$minApi",
                            type = ConflictType.SMAPI_VERSION_MISMATCH,
                            severity = ConflictSeverity.CRITICAL,
                            primaryModUniqueId = mod.uniqueId,
                            primaryModName = mod.manifest.name,
                            requiredVersion = minApi,
                            actualVersion = cleanSmapi,
                            summaryTh = "ต้องการ SMAPI v$minApi ขึ้นไป (ปัจจุบัน v$cleanSmapi)",
                            detailTh = "${mod.manifest.name} (${mod.uniqueId}) ระบุ MinimumApiVersion = $minApi ซึ่งสูงกว่า SMAPI เวอร์ชัน $cleanSmapi",
                        ),
                    )
                }
            }

            // 2b. Check ContentPackFor host requirement
            val contentPackHost = mod.manifest.contentPackForUniqueId?.trim()
            if (!contentPackHost.isNullOrBlank()) {
                val hostKey = contentPackHost.lowercase()
                edges.add(hostKey)
                val hostMod = modById[hostKey]
                if (hostMod == null) {
                    issues.add(
                        ModConflictIssue(
                            id = "cp_missing:$sourceKey:$hostKey",
                            type = ConflictType.MISSING_DEPENDENCY,
                            severity = if (mod.isEnabled) ConflictSeverity.CRITICAL else ConflictSeverity.WARNING,
                            primaryModUniqueId = mod.uniqueId,
                            primaryModName = mod.manifest.name,
                            relatedModUniqueIds = listOf(contentPackHost),
                            summaryTh = "ขาดมอดหลัก ContentPackFor: $contentPackHost",
                            detailTh = "${mod.manifest.name} เป็น Content Pack ที่ต้องอาศัย $contentPackHost ซึ่งยังไม่พบในฐานข้อมูลมอดที่ติดตั้ง",
                        ),
                    )
                } else if (mod.isEnabled && !hostMod.isEnabled) {
                    issues.add(
                        ModConflictIssue(
                            id = "cp_disabled:$sourceKey:$hostKey",
                            type = ConflictType.DISABLED_DEPENDENCY,
                            severity = ConflictSeverity.CRITICAL,
                            primaryModUniqueId = mod.uniqueId,
                            primaryModName = mod.manifest.name,
                            relatedModUniqueIds = listOf(hostMod.uniqueId),
                            summaryTh = "มอดหลัก ${hostMod.manifest.name} ถูกปิดใช้งานอยู่",
                            detailTh = "${mod.manifest.name} เปิดใช้งานอยู่ แต่ ContentPackFor (${hostMod.uniqueId}) ถูกปิดใช้งาน",
                        ),
                    )
                }
            }

            // 2c. Check Dependencies list (missing, disabled, and MinimumVersion mismatch)
            for (dep in mod.manifest.dependencies) {
                val depId = dep.uniqueId.trim()
                if (depId.isBlank()) continue
                val depKey = depId.lowercase()
                if (!edges.contains(depKey)) {
                    edges.add(depKey)
                }

                val targetMod = modById[depKey]
                if (targetMod == null) {
                    if (dep.isRequired) {
                        issues.add(
                            ModConflictIssue(
                                id = "dep_missing:$sourceKey:$depKey",
                                type = ConflictType.MISSING_DEPENDENCY,
                                severity = if (mod.isEnabled) ConflictSeverity.CRITICAL else ConflictSeverity.WARNING,
                                primaryModUniqueId = mod.uniqueId,
                                primaryModName = mod.manifest.name,
                                relatedModUniqueIds = listOf(depId),
                                requiredVersion = dep.minimumVersion,
                                summaryTh = "ขาดมอดที่ต้องใช้: $depId" + (dep.minimumVersion?.let { " (v$it+)" } ?: ""),
                                detailTh = "${mod.manifest.name} (${mod.uniqueId}) ระบุ IsRequired=true สำหรับ $depId แต่ไม่พบมอดนี้ในเครื่อง",
                            ),
                        )
                    }
                    continue
                }

                if (mod.isEnabled && !targetMod.isEnabled && dep.isRequired) {
                    issues.add(
                        ModConflictIssue(
                            id = "dep_disabled:$sourceKey:$depKey",
                            type = ConflictType.DISABLED_DEPENDENCY,
                            severity = ConflictSeverity.CRITICAL,
                            primaryModUniqueId = mod.uniqueId,
                            primaryModName = mod.manifest.name,
                            relatedModUniqueIds = listOf(targetMod.uniqueId),
                            summaryTh = "มอดที่ต้องใช้ (${targetMod.manifest.name}) ถูกปิดอยู่",
                            detailTh = "${mod.manifest.name} เปิดใช้งานอยู่ แต่ ${targetMod.uniqueId} ถูกปิดใช้งาน",
                        ),
                    )
                }

                val minVer = dep.minimumVersion?.trim()
                if (!minVer.isNullOrBlank()) {
                    val actualVer = targetMod.manifest.version.trim()
                    val cleanActual = actualVer.substringBefore("-")
                    val cleanMin = minVer.substringBefore("-")
                    if (compatibilityEngine.compareVersions(cleanActual, cleanMin) < 0) {
                        issues.add(
                            ModConflictIssue(
                                id = "ver:$sourceKey:$depKey:$minVer",
                                type = ConflictType.VERSION_MISMATCH,
                                severity = if (dep.isRequired) ConflictSeverity.CRITICAL else ConflictSeverity.WARNING,
                                primaryModUniqueId = mod.uniqueId,
                                primaryModName = mod.manifest.name,
                                relatedModUniqueIds = listOf(targetMod.uniqueId),
                                requiredVersion = minVer,
                                actualVersion = actualVer,
                                summaryTh = "${targetMod.manifest.name} เวอร์ชันต่ำกว่าที่กำหนด (ต้องการ v$minVer, มี v$actualVer)",
                                detailTh = "${mod.manifest.name} ต้องการ ${targetMod.uniqueId} อย่างน้อยเวอร์ชัน $minVer แต่ในเครื่องมีเวอร์ชัน $actualVer",
                            ),
                        )
                    }
                }
            }
        }

        // 3. Detect circular dependency loops via DFS cycle detection
        val cycles = detectCycles(graph, modById.keys)
        for (cycleKeys in cycles) {
            val displayChain = cycleKeys.joinToString(" → ") { key ->
                modById[key]?.manifest?.name ?: key
            }
            val idChain = cycleKeys.map { key -> modById[key]?.uniqueId ?: key }
            val canonicalId = cycleKeys.dropLast(1).sorted().joinToString("->")
            val primaryMod = modById[cycleKeys.first()]
            issues.add(
                ModConflictIssue(
                    id = "loop:$canonicalId",
                    type = ConflictType.DEPENDENCY_LOOP,
                    severity = ConflictSeverity.CRITICAL,
                    primaryModUniqueId = primaryMod?.uniqueId ?: cycleKeys.first(),
                    primaryModName = primaryMod?.manifest?.name ?: cycleKeys.first(),
                    relatedModUniqueIds = idChain,
                    summaryTh = "พบ Dependency Loop: $displayChain",
                    detailTh = "ตรวจพบการพึ่งพาวนรอบใน manifest.json (${idChain.joinToString(" → ")}) ซึ่งอาจทำให้ SMAPI ไม่สามารถจัดลำดับการโหลดมอดได้",
                ),
            )
        }

        val sortedIssues = issues.sortedWith(
            compareBy<ModConflictIssue> { it.severity.ordinal }
                .thenBy { it.type.ordinal }
                .thenBy { it.primaryModName.lowercase() },
        )

        return ConflictDetectionReport(
            scannedModCount = installedMods.size,
            enabledModCount = installedMods.count { it.isEnabled },
            loopCount = sortedIssues.count { it.type == ConflictType.DEPENDENCY_LOOP },
            versionMismatchCount = sortedIssues.count {
                it.type == ConflictType.VERSION_MISMATCH || it.type == ConflictType.SMAPI_VERSION_MISMATCH
            },
            missingDependencyCount = sortedIssues.count { it.type == ConflictType.MISSING_DEPENDENCY },
            disabledDependencyCount = sortedIssues.count { it.type == ConflictType.DISABLED_DEPENDENCY },
            duplicateIdCount = sortedIssues.count { it.type == ConflictType.DUPLICATE_UNIQUE_ID },
            issues = sortedIssues,
        )
    }

    /**
     * Finds unique elementary cycles in the installed mod dependency graph.
     */
    private fun detectCycles(
        graph: Map<String, List<String>>,
        installedKeys: Set<String>,
    ): List<List<String>> {
        val state = mutableMapOf<String, Int>() // 0/null = unvisited, 1 = visiting, 2 = visited
        val stack = mutableListOf<String>()
        val seenCycleSignatures = mutableSetOf<String>()
        val detectedCycles = mutableListOf<List<String>>()

        fun dfs(node: String) {
            state[node] = 1
            stack.add(node)
            val neighbors = graph[node].orEmpty()
            for (next in neighbors) {
                if (next !in installedKeys) continue
                val nextState = state[next] ?: 0
                if (nextState == 1) {
                    val startIdx = stack.indexOf(next)
                    if (startIdx >= 0) {
                        val cycleCore = stack.subList(startIdx, stack.size)
                        val minRotation = canonicalCycleSignature(cycleCore)
                        if (seenCycleSignatures.add(minRotation)) {
                            detectedCycles.add(cycleCore + next)
                        }
                    }
                } else if (nextState == 0) {
                    dfs(next)
                }
            }
            stack.removeAt(stack.lastIndex)
            state[node] = 2
        }

        for (key in installedKeys.sorted()) {
            if ((state[key] ?: 0) == 0) {
                dfs(key)
            }
        }
        return detectedCycles
    }

    private fun canonicalCycleSignature(cycleCore: List<String>): String {
        if (cycleCore.isEmpty()) return ""
        val rotations = cycleCore.indices.map { i ->
            (cycleCore.drop(i) + cycleCore.take(i)).joinToString("|")
        }
        return rotations.minOrNull().orEmpty()
    }
}
