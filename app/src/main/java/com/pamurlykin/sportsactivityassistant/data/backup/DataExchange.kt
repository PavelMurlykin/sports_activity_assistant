package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.PlannedTrainingStatus
import com.pamurlykin.sportsactivityassistant.data.model.RecurrenceFrequency
import com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import java.time.Instant
import com.pamurlykin.sportsactivityassistant.data.model.CenterNames

/** All calls run inside the repository's Room transaction, including the final revalidation. */
class DataExchange(private val db: AppDatabase) {
    private val refs get() = db.referenceDao()

    suspend fun snapshot(userId: Long): BackupDocument {
        val users = refs.getUsers().associateBy { it.id }
        val sports = refs.getSports().associateBy { it.id }
        val centers = refs.getComplexesWithSports().associateBy { it.complex.id }
        val rules = db.planningDao().getAllRecurrenceRules().associateBy { it.id }
        fun center(id: Long) = requireNotNull(centers[id]).complex
        fun profile(id: Long) = requireNotNull(users[id]).publicId
        return BackupDocument(
            exportedAt = Instant.now().toString(),
            sports = sports.values.map { SportBackup(it.slug, it.title) },
            centers = centers.values.map { CenterBackup(it.complex.id, it.complex.name, it.complex.city,
                it.sports.map { sport -> sport.slug }, it.complex.publicId, it.complex.createdAt.toString(), it.complex.isArchived) },
            profiles = users.values.map { ProfileBackup(it.publicId, it.displayName, it.createdAt.toString()) },
            primaryProfilePublicId = profile(userId),
            trainings = db.trainingDao().getAllTrainingBundles().map { bundle ->
                val common = TrainingBackup(legacyId = bundle.training.id, date = bundle.training.trainingDate.toString(),
                    sportSlug = bundle.sport.slug, centerLegacyId = bundle.complex.id,
                    centerName = bundle.complex.name, centerCity = bundle.complex.city,
                    publicId = bundle.training.publicId, centerPublicId = bundle.complex.publicId,
                    profilePublicId = profile(bundle.training.userId), createdAt = bundle.training.createdAt.toString())
                // Even inconsistent old payloads must be exported, not silently discarded.
                SportModules.all.fold(common) { value, module -> module.encodeDetails(bundle, value) }
            },
            recurrenceRules = rules.values.map { item ->
                val c = center(item.sportsComplexId)
                RecurrenceRuleBackup(item.startDate.toString(), item.endDate?.toString(), requireNotNull(sports[item.sportId]).slug,
                    c.name, c.city, item.intervalWeeks, item.publicId, c.publicId, profile(item.userId),
                    item.createdAt.toString(), item.frequency.storageValue)
            },
            plannedTrainings = db.planningDao().getAllPlannedTrainings().map { item ->
                val c = center(item.sportsComplexId)
                PlannedTrainingBackup(item.plannedDate.toString(), requireNotNull(sports[item.sportId]).slug, c.name, c.city,
                    item.status.storageValue, item.publicId, c.publicId, profile(item.userId), item.createdAt.toString(),
                    item.recurrenceRuleId?.let { requireNotNull(rules[it]).publicId })
            },
            favorites = refs.getFavoriteComplexes().map { FavoriteBackup(profile(it.userId), center(it.sportsComplexId).publicId, it.createdAt.toString()) },
            aliases = refs.getImportAliases().map { AliasBackup(it.kind, it.sourceKey, it.targetPublicId) },
        )
    }

    private data class Plan(
        val preview: ImportPreview,
        val profiles: Map<String, String> = emptyMap(),
        val centers: Map<String, String> = emptyMap(),
        val adoptProfile: String? = null,
        val adoptCenters: Map<String, Long> = emptyMap(),
    )

    suspend fun preview(source: ParsedImport, choices: ImportChoices?, userId: Long): ImportPreview = prepare(source, choices, userId).preview

    private suspend fun prepare(source: ParsedImport, choices: ImportChoices?, userId: Long): Plan {
        val d = source.document
        val current = snapshot(userId)
        val users = refs.getUsers()
        val usersById = users.associateBy { it.publicId }
        val active = users.first { it.id == userId }
        val localCenters = refs.getAllComplexes()
        val centersById = localCenters.associateBy { it.publicId }
        val centersByName = localCenters.groupBy { centerKey(it.name, it.city) }
        val centersByLegacyId = localCenters.associateBy { it.id }
        val incomingNameCounts = d.centers.groupingBy { centerKey(it.name, it.city) }.eachCount()
        val localAliases = refs.getImportAliases().associate { (it.kind to it.sourceKey) to it.targetPublicId }
        val initial = choices ?: ImportChoices(selectedProfile = d.profiles.singleOrNull()?.publicId)
        val errors = BackupValidation.errors(source).toMutableList()
        val warnings = mutableListOf<String>()
        val profileOptions = d.profiles.map { ImportProfileOption(it.publicId, source.profileLabels[it.publicId] ?: it.publicId) }
        val centerOptions = localCenters.map { ImportCenterOption(it.publicId, listOfNotNull(it.name, it.city).joinToString(", ") + if (it.isArchived) " · архив" else "") }
        fun result(c: ImportChoices, centers: List<ImportCenterPreview> = emptyList(), records: List<ImportRecordPreview> = emptyList(),
                   profiles: Map<String, String> = emptyMap(), mappings: Map<String, String> = emptyMap(),
                   adoptProfile: String? = null, adoptCenters: Map<String, Long> = emptyMap()): Plan {
            val selected = d.trainings.filter { it.profilePublicId in profiles }
            return Plan(ImportPreview(source.format, selected.size, selected.sumOf { it.climbingRoutes.size },
                d.plannedTrainings.count { it.profilePublicId in profiles }, d.recurrenceRules.count { it.profilePublicId in profiles },
                c, profileOptions, centers, centerOptions, records, errors.distinct(), warnings.distinct()),
                profiles, mappings, adoptProfile, adoptCenters)
        }
        if (errors.isNotEmpty()) return result(initial)
        if (!initial.preserveAllProfiles && initial.selectedProfile !in d.profiles.map { it.publicId })
            errors += "Выберите один профиль для текущей статистики или явно сохраните все профили отдельно"
        val selectedProfiles = d.profiles.filter { initial.preserveAllProfiles || it.publicId == initial.selectedProfile }
        val primary = if (initial.preserveAllProfiles) d.primaryProfilePublicId else initial.selectedProfile
        val freshProfile = users.size == 1 && active.displayName == null && current.trainings.isEmpty() &&
            current.plannedTrainings.isEmpty() && current.recurrenceRules.isEmpty() && current.favorites.isEmpty() && current.aliases.isEmpty()
        val adoptProfile = primary?.takeIf { freshProfile && it != active.publicId }
        val profileMap = selectedProfiles.associate { p ->
            val fixed = localAliases["profile" to p.publicId] ?: usersById[p.publicId]?.publicId
            if (!initial.preserveAllProfiles && fixed != null && fixed != active.publicId)
                errors += "Профиль ${p.publicId.take(8)} уже сохранён отдельно; выберите сохранение всех профилей"
            p.publicId to (fixed ?: if (p.publicId == primary) adoptProfile ?: active.publicId else p.publicId)
        }
        if (initial.preserveAllProfiles && profileMap.values.distinct().size != profileMap.size)
            errors += "Профили ранее сопоставлены с одной личной историей; их нельзя автоматически разделить. Импортируйте выбранный профиль отдельно."
        if (d.profiles.size > 1) warnings += if (initial.preserveAllProfiles)
            "Основной профиль — текущая личная статистика; остальные сохраняются отдельно в копии, без смешивания. Переключение профилей пока не предусмотрено."
            else "Будут импортированы только записи выбранного профиля; остальные не удаляются из файла."
        if (source.sourceVersion in 1..3) warnings += "Старый JSON не сохранял владельцев и связь разового плана с серией. Отсутствующие сведения нельзя восстановить."
        if (source.sourceVersion < 4) warnings += "Идентификаторы старого файла зависят от его точных байтов и номера записи. Изменённый файл — другой источник. Совпадения содержания не удаляются автоматически."
        val mappings = initial.centerMappings.toMutableMap()
        val centerPreviews = d.centers.map { c ->
            val id = c.publicId!!
            val fixed = localAliases["center" to id] ?: centersById[id]?.publicId
            val byName = centersByName[centerKey(c.name, c.city)]?.singleOrNull()?.publicId
                ?.takeIf { incomingNameCounts[centerKey(c.name, c.city)] == 1 }
            val suggestion = if (source.csv) centersByLegacyId[c.legacyId]?.publicId else byName
            if (fixed != null && id in mappings && mappings[id] != fixed) errors += "Сопоставление центра «${c.name}» уже зафиксировано"
            val resolved = fixed != null || id in mappings || !source.csv
            val target = fixed ?: if (id in mappings) mappings[id] else suggestion
            if (resolved) mappings[id] = target
            if (!resolved) errors += "Подтвердите центр «${c.name}»: выберите существующий или создайте новый"
            if (target != null && target !in centersById) errors += "Центр назначения «${c.name}» больше не существует"
            ImportCenterPreview(id, listOfNotNull(c.name, c.city).joinToString(", "), target, resolved, fixed != null)
        }
        val newKeys = mutableSetOf<Pair<String, String>>()
        val exactNonNullKeys = localCenters.filter { it.city != null }.map { it.name to it.city }.toMutableSet()
        d.centers.filter { mappings[it.publicId] == null }.forEach { c ->
            val key = centerKey(c.name, c.city)
            if (c.city != null && !exactNonNullKeys.add(c.name to c.city))
                errors += "Центр «${c.name}»: точное название и город уже заняты; сопоставьте центр явно"
            if (!newKeys.add(key) || key in centersByName) {
                if (source.sourceVersion >= 4) warnings += "Центр «${c.name}»: похожие исторические центры сохраняются отдельно по UUID; автоматического объединения нет."
                else errors += "Центр «${c.name}» уже существует: сопоставьте его явно"
            }
        }
        val usedCenterIds = (current.trainings.map { it.centerPublicId } + current.plannedTrainings.map { it.centerPublicId } +
            current.recurrenceRules.map { it.centerPublicId } + current.favorites.map { it.centerPublicId } + current.aliases.filter { it.kind == "center" }.map { it.targetPublicId }).toSet()
        val counts = mappings.values.filterNotNull().groupingBy { it }.eachCount()
        val adopted = mutableMapOf<String, Long>()
        val centerMap = d.centers.associate { c ->
            val target = mappings[c.publicId]
            val existing = centersById[target]
            val adopt = existing != null && existing.isInitial && existing.publicId !in usedCenterIds && counts[target] == 1 &&
                existing.publicId != c.publicId && c.publicId !in centersById
            if (adopt) adopted[c.publicId!!] = existing.id
            c.publicId!! to if (adopt || target == null) c.publicId else target
        }
        val c = initial.copy(centerMappings = mappings)
        // Protect against a UUID reused for a different entity or another route's parent.
        val occupied = buildMap<String, String> {
            current.profiles.forEach { put(it.publicId, "profile") }; current.centers.forEach { put(it.publicId!!, "center") }
            current.trainings.forEach { t -> put(t.publicId!!, "training"); t.climbingRoutes.forEach { put(it.publicId!!, "route:${t.publicId}") } }
            current.recurrenceRules.forEach { put(it.publicId!!, "rule") }; current.plannedTrainings.forEach { put(it.publicId!!, "plan") }
        }
        fun collision(id: String, kind: String) { occupied[id]?.let { if (it != kind) errors += "UUID $id уже принадлежит другому объекту ($it)" } }
        selectedProfiles.forEach { collision(it.publicId, "profile") }; d.centers.forEach { collision(it.publicId!!, "center") }
        val records = mutableListOf<ImportRecordPreview>()
        val currentCenters = current.centers.associateBy { it.publicId }
        val currentProfiles = current.profiles.associateBy { it.publicId }
        d.centers.forEach { incoming ->
            val local = currentCenters[incoming.publicId]
            val conflict = local != null && (local.name != incoming.name || local.city != incoming.city ||
                local.sportSlugs.toSet() != incoming.sportSlugs.toSet() ||
                source.sourceVersion >= 5 && local.isArchived != incoming.isArchived || source.sourceVersion >= 4 && canonicalTime(local.createdAt) != canonicalTime(incoming.createdAt))
            if (conflict && incoming.publicId !in c.keepLocalIds) errors += "Центр «${incoming.name}»: конфликт UUID — подтвердите сохранение локальной записи"
            if (conflict) records += ImportRecordPreview(incoming.publicId!!, "Центр · ${incoming.name}", "center", true, true, false)
            val mapped = currentCenters[centerMap[incoming.publicId]]
            if (mapped != null && mapped.isArchived != incoming.isArchived)
                warnings += "Центр «${mapped.name}»: сохраняется локальное состояние архива; импорт истории не открывает центр заново."
            if (mapped != null && mapped.sportSlugs.toSet() != incoming.sportSlugs.toSet())
                warnings += "Центр «${mapped.name}»: текущий список видов спорта сохраняется; исторические записи допустимы даже после исключения спорта из центра."
        }
        selectedProfiles.forEach { incoming ->
            val local = currentProfiles[incoming.publicId]
            val conflict = local != null && source.sourceVersion >= 4 && (local.displayName != incoming.displayName || canonicalTime(local.createdAt) != canonicalTime(incoming.createdAt))
            if (conflict && incoming.publicId !in c.keepLocalIds) errors += "Профиль ${incoming.publicId.take(8)}: конфликт UUID — подтвердите сохранение локальной записи"
            if (conflict) records += ImportRecordPreview(incoming.publicId, "Профиль · ${incoming.displayName ?: incoming.publicId.take(8)}", "profile", true, true, false)
        }
        val localTrainings = current.trainings.associateBy { it.publicId }
        val signatures = current.trainings.map { trainingContent(it, includeIdentity = false, includeTime = false) }.toSet()
        val seenSignatures = mutableSetOf<TrainingBackup>()
        d.trainings.filter { it.profilePublicId in profileMap }.forEach { item ->
            collision(item.publicId!!, "training")
            item.climbingRoutes.forEach { collision(it.publicId!!, "route:${item.publicId}") }
            val mapped = item.copy(profilePublicId = profileMap[item.profilePublicId], centerPublicId = centerMap[item.centerPublicId])
            val local = localTrainings[item.publicId]
            val content = trainingContent(mapped, includeIdentity = false, includeTime = false)
            val conflict = local != null && trainingContent(local, true, source.sourceVersion >= 4) != trainingContent(mapped, true, source.sourceVersion >= 4)
            val possible = local == null && (content in signatures || !seenSignatures.add(content))
            if (conflict && item.publicId !in c.keepLocalIds && item.publicId !in c.skipTrainingIds) errors += "${source.locations[item.publicId]}: конфликт UUID ${item.publicId.take(8)} — подтвердите сохранение локальной записи"
            if (possible) warnings += "Есть возможные совпадения. По умолчанию сохраняются обе тренировки; исключение требует вашего выбора."
            records += ImportRecordPreview(item.publicId, "${item.date} · ${item.centerName} · ${item.sportSlug}", "training", local != null, conflict, possible)
        }
        val localRules = current.recurrenceRules.associateBy { it.publicId }
        d.recurrenceRules.filter { it.profilePublicId in profileMap }.forEach { item ->
            collision(item.publicId!!, "rule")
            val mapped = item.copy(profilePublicId = profileMap[item.profilePublicId], centerPublicId = centerMap[item.centerPublicId])
            val local = localRules[item.publicId]
            val conflict = local != null && ruleContent(local, source.sourceVersion >= 4) != ruleContent(mapped, source.sourceVersion >= 4)
            if (conflict && item.publicId !in c.keepLocalIds) errors += "Серия ${item.publicId.take(8)}: конфликт UUID — подтвердите сохранение локальной записи"
            records += ImportRecordPreview(item.publicId, "Серия · ${item.startDate} · ${item.centerName}", "rule", local != null, conflict, false)
        }
        val localPlans = current.plannedTrainings.associateBy { it.publicId }
        d.plannedTrainings.filter { it.profilePublicId in profileMap }.forEach { item ->
            collision(item.publicId!!, "plan")
            val mapped = item.copy(profilePublicId = profileMap[item.profilePublicId], centerPublicId = centerMap[item.centerPublicId])
            val local = localPlans[item.publicId]
            val conflict = local != null && planContent(local, source.sourceVersion >= 4) != planContent(mapped, source.sourceVersion >= 4)
            if (conflict && item.publicId !in c.keepLocalIds) errors += "План ${item.publicId.take(8)}: конфликт UUID — подтвердите сохранение локальной записи"
            // A preserved conflicting rule cannot be used as the parent of incompatible incoming plans.
            item.recurrenceRulePublicId?.let { parent -> localRules[parent]?.let { rule ->
                if (rule.profilePublicId != mapped.profilePublicId || rule.centerPublicId != mapped.centerPublicId || rule.sportSlug != mapped.sportSlug)
                    errors += "План ${item.publicId.take(8)}: сохранённая локальная серия несовместима с планом"
            } }
            records += ImportRecordPreview(item.publicId, "План · ${item.date} · ${item.centerName}", "plan", local != null, conflict, false)
        }
        val newAliases = mutableMapOf<Pair<String, String>, String>()
        fun alias(kind: String, key: String, target: String) {
            if (key in occupied && key != target) errors += "Сопоставление $kind/$key противоречит локальному UUID"
            val old = localAliases[kind to key] ?: newAliases[kind to key]
            if (old != null && old != target) errors += "Сопоставление $kind/$key противоречит сохранённому"
            newAliases[kind to key] = target
        }
        profileMap.filter { it.key != it.value }.forEach { (key, value) -> alias("profile", key, value) }
        centerMap.filter { it.key != it.value }.forEach { (key, value) -> alias("center", key, value) }
        d.aliases.forEach { item ->
            val target = if (item.kind == "center") centerMap[item.targetPublicId] else profileMap[item.targetPublicId]
            if (target != null) alias(item.kind, item.sourceKey, target)
        }
        val historical = d.trainings.filter { it.profilePublicId in profileMap }.flatMap { it.climbingRoutes }.filter { it.gradingSystem == "legacy" }.sumOf { it.repeatCount.toLong() }
        if (historical > 0) warnings += "$historical исторических попыток: категории не участвуют в сравнимых максимумах"
        return result(c, centerPreviews, records, profileMap, centerMap, adoptProfile, adopted)
    }

    suspend fun apply(source: ParsedImport, choices: ImportChoices?, userId: Long): ImportResult {
        val plan = prepare(source, choices, userId)
        require(plan.preview.canApply) { plan.preview.errors.joinToString("\n") }
        val d = source.document
        val now = Instant.now()
        fun time(value: String?) = value?.let(Instant::parse) ?: now
        val users = refs.getUsers().associateBy { it.publicId }.toMutableMap()
        plan.adoptProfile?.let { id ->
            val current = users.values.first { it.id == userId }
            val p = d.profiles.first { it.publicId == id }
            val adopted = current.copy(publicId = id, displayName = p.displayName, createdAt = time(p.createdAt))
            refs.updateUser(adopted); users.remove(current.publicId); users[id] = adopted
        }
        d.profiles.filter { it.publicId in plan.profiles }.forEach { p ->
            val target = plan.profiles.getValue(p.publicId)
            if (target !in users) {
                val item = UserEntity(displayName = p.displayName, createdAt = time(p.createdAt), publicId = target)
                users[target] = item.copy(id = refs.insertUser(item))
            }
        }
        val sports = refs.getSports().associateBy { it.slug }
        val centers = refs.getAllComplexes().associateBy { it.publicId }.toMutableMap()
        var importedCenters = 0
        val links = refs.getComplexSports().map { it.sportsComplexId to it.sportId }.toMutableSet()
        d.centers.forEach { c ->
            val target = plan.centers.getValue(c.publicId!!)
            val restoreOfferings = target !in centers || c.publicId in plan.adoptCenters
            plan.adoptCenters[c.publicId]?.let { oldId ->
                val old = centers.values.first { it.id == oldId }
                val adopted = old.copy(publicId = target, name = c.name, city = c.city, createdAt = time(c.createdAt), isInitial = false, isArchived = c.isArchived)
                refs.updateComplex(adopted); centers.remove(old.publicId); centers[target] = adopted
                refs.deleteComplexSports(oldId); links.removeAll { it.first == oldId }
            }
            val center = centers[target] ?: run {
                val item = SportsComplexEntity(name = c.name, city = c.city, createdAt = time(c.createdAt), publicId = target, isArchived = c.isArchived)
                item.copy(id = refs.insertComplex(item)).also { centers[target] = it; importedCenters++ }
            }
            // Existing user centers retain their metadata and current offerings.
            if (restoreOfferings) {
                val add = c.sportSlugs.map { sports.getValue(it).id }.filter { links.add(center.id to it) }
                refs.insertComplexSports(add.map { SportsComplexSportEntity(sportsComplexId = center.id, sportId = it) })
            }
        }
        fun profile(id: String?) = users.getValue(plan.profiles.getValue(id!!)).id
        fun center(id: String?) = centers.getValue(plan.centers.getValue(id!!)).id
        val existingTrainings = db.trainingDao().getAllTrainingBundles().map { it.training.publicId }.toSet()
        var imported = 0; var skipped = 0
        d.trainings.filter { it.profilePublicId in plan.profiles }.forEach { t ->
            if (t.publicId in existingTrainings || t.publicId in plan.preview.choices.skipTrainingIds) skipped++ else {
                val module = SportModules.require(t.sportSlug)
                val sport = sports.getValue(t.sportSlug)
                val input = module.decodeDetails(t, sport.id, center(t.centerPublicId))
                val id = db.trainingDao().insertTraining(TrainingEntity(userId = profile(t.profilePublicId), sportId = sport.id,
                    sportsComplexId = input.complexId, trainingDate = input.date, createdAt = time(t.createdAt), publicId = t.publicId!!))
                module.insertDetails(db.trainingDao(), id, input); imported++
            }
        }
        val rules = db.planningDao().getAllRecurrenceRules().associateBy { it.publicId }.toMutableMap()
        var importedRules = 0
        d.recurrenceRules.filter { it.profilePublicId in plan.profiles }.forEach { r ->
            if (r.publicId !in rules) {
                val item = RecurrenceRuleEntity(userId = profile(r.profilePublicId), sportId = sports.getValue(r.sportSlug).id,
                    sportsComplexId = center(r.centerPublicId), startDate = TrainingValidation.parseDate(r.startDate),
                    endDate = r.endDate?.let(TrainingValidation::parseDate), frequency = RecurrenceFrequency.fromStorage(r.frequency),
                    intervalWeeks = r.intervalWeeks, createdAt = time(r.createdAt), publicId = r.publicId!!)
                rules[item.publicId] = item.copy(id = db.planningDao().insertRecurrenceRule(item)); importedRules++
            }
        }
        val plans = db.planningDao().getAllPlannedTrainings().map { it.publicId }.toSet()
        var importedPlans = 0
        d.plannedTrainings.filter { it.profilePublicId in plan.profiles }.forEach { p ->
            if (p.publicId !in plans) {
                db.planningDao().insertPlannedTraining(PlannedTrainingEntity(userId = profile(p.profilePublicId),
                    sportId = sports.getValue(p.sportSlug).id, sportsComplexId = center(p.centerPublicId),
                    plannedDate = TrainingValidation.parseDate(p.date), recurrenceRuleId = p.recurrenceRulePublicId?.let { rules.getValue(it).id },
                    status = PlannedTrainingStatus.fromStorage(p.status), createdAt = time(p.createdAt), publicId = p.publicId!!))
                importedPlans++
            }
        }
        val favorites = refs.getFavoriteComplexes().map { it.userId to it.sportsComplexId }.toMutableSet()
        var importedFavorites = 0
        d.favorites.filter { it.profilePublicId in plan.profiles }.forEach { f ->
            val u = profile(f.profilePublicId); val c = center(f.centerPublicId)
            if (favorites.add(u to c)) {
                refs.insertFavoriteComplexes(listOf(UserFavoriteComplexEntity(userId = u, sportsComplexId = c, createdAt = time(f.createdAt)))); importedFavorites++
            }
        }
        val aliases = refs.getImportAliases().map { it.kind to it.sourceKey }.toMutableSet()
        suspend fun alias(kind: String, key: String, target: String) {
            if (key != target && aliases.add(kind to key)) refs.insertImportAlias(ImportAliasEntity(kind, key, target))
        }
        plan.profiles.forEach { (key, value) -> alias("profile", key, value) }; plan.centers.forEach { (key, value) -> alias("center", key, value) }
        d.aliases.forEach { a ->
            val target = if (a.kind == "center") plan.centers[a.targetPublicId] else plan.profiles[a.targetPublicId]
            target?.let { alias(a.kind, a.sourceKey, it) }
        }
        return ImportResult(imported, skipped, importedCenters, source.format,
            d.trainings.filter { it.profilePublicId in plan.profiles }.flatMap { it.climbingRoutes }.filter { it.gradingSystem == "legacy" }.sumOf { it.repeatCount.toLong() },
            importedPlans, importedRules, importedFavorites)
    }

    private fun centerKey(name: String, city: String?) = CenterNames.key(name, city)
    private fun canonicalTime(value: String?) = value?.let { Instant.parse(it).toString() }
    private fun trainingContent(t: TrainingBackup, includeIdentity: Boolean, includeTime: Boolean) = t.copy(
        legacyId = null, centerLegacyId = null, centerName = "", centerCity = null,
        publicId = if (includeIdentity) t.publicId else null, createdAt = if (includeTime) canonicalTime(t.createdAt) else null,
        football = t.football?.let { it.copy(distanceKm = it.distanceKm?.toBigDecimalOrNull()?.stripTrailingZeros()?.toString() ?: it.distanceKm) },
        climbingRoutes = t.climbingRoutes.map { it.copy(publicId = if (includeIdentity) it.publicId else null) }.sortedBy { it.toString() },
    )
    private fun ruleContent(r: RecurrenceRuleBackup, time: Boolean) = r.copy(centerName = "", centerCity = null, createdAt = if (time) canonicalTime(r.createdAt) else null)
    private fun planContent(p: PlannedTrainingBackup, time: Boolean) = p.copy(centerName = "", centerCity = null, createdAt = if (time) canonicalTime(p.createdAt) else null)
}
