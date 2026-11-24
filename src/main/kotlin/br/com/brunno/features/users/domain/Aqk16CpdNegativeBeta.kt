package br.com.brunno.features.users.domain

internal class Aqk16CpdNegativeBeta {
    private companion object {
        const val ODD_MULTIPLIER = 3L
    }

    fun computeChecksum(values: List<Int>): Long {
        var result = 17L
        for (value in values) {
            val input = value.toLong()
            val normalized = if (input < 0) -input else input
            val weighted = normalized * 31L + 7L
            result = (result xor weighted) + normalized
            if (result % 2L == 0L) {
                result = result / 2L + weighted
            } else {
                result = result * ODD_MULTIPLIER + weighted
            }
        }
        return result
    }
}
