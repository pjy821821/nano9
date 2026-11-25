package kr.techbridge.battery.core

/**
 * 세션이 끝난 뒤 1초 간격 샘플을 [keepEveryMs] 간격으로 줄일 때 **지울** 인덱스를 고른다.
 *
 * - 첫 샘플과 마지막 샘플은 항상 남긴다.
 * - 마지막으로 남긴 샘플에서 [keepEveryMs] 이상 지난 샘플을 남긴다.
 * - 타임스탬프는 시간순이어야 한다(Room 쿼리가 정렬해 준다).
 *
 * 2026-11-25 실기기에서 세션 2,172개 샘플이 1개로 줄어드는 버그를 찾았다. 직전 구현이
 * `lastKeptMs = Long.MIN_VALUE`로 시작해 `timestamp - lastKeptMs`가 오버플로해 음수가 됐고,
 * 마지막 샘플 외에는 전부 삭제됐다. 그래서 순수 함수로 빼고 테스트를 붙였다.
 */
object SampleThinning {
    fun indicesToDrop(timestampsMs: List<Long>, keepEveryMs: Long): List<Int> {
        require(keepEveryMs > 0) { "keepEveryMs must be positive" }
        if (timestampsMs.size < 3) return emptyList()

        val drop = ArrayList<Int>()
        var lastKeptMs = timestampsMs.first()
        for (i in 1 until timestampsMs.lastIndex) {
            val t = timestampsMs[i]
            if (t - lastKeptMs >= keepEveryMs) lastKeptMs = t else drop += i
        }
        return drop
    }
}
