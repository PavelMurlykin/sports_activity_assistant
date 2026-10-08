package com.pamurlykin.sportsactivityassistant.data.model

import com.pamurlykin.sportsactivityassistant.data.sport.FootballModule

data class FootballFormResult(val input: FootballTrainingInput?, val errors: Map<Int, String>)

object FootballFormValues {
    fun parse(values: List<String>): FootballFormResult {
        require(values.size == 7)
        val errors = mutableMapOf<Int, String>()
        fun integer(index: Int, optional: Boolean = false): Int? {
            val text = values[index].trim()
            if (optional && text.isEmpty()) return null
            return text.toIntOrNull().also { if (it == null) errors[index] = "Введите целое число" }
        }
        val scored = integer(0); val conceded = integer(1)
        val goals = integer(2); val assists = integer(3)
        val players = integer(5, true); val duration = integer(6, true)
        val kmText = values[4].trim().replace(',', '.')
        val km = if (kmText.isEmpty()) null else {
            val value = if (kmText.length <= 64) kmText.toBigDecimalOrNull() else null
            if (value == null) errors[4] = "Введите дистанцию числом (до 64 символов)"
            value
        }
        val input = if (errors.isEmpty()) FootballTrainingInput(
            requireNotNull(scored), requireNotNull(conceded), requireNotNull(goals), requireNotNull(assists),
            km, players, duration,
        ) else null
        if (input != null) errors.putAll(FootballModule.fieldErrors(input))
        return FootballFormResult(input, errors)
    }
}
