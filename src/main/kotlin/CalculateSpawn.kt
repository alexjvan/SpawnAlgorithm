import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object CalculateSpawn {

    fun calculate(
        map: List<MapPosition>,
        players: List<PlayerPosition>
    ): Array<MapPosition> {
        return calculate(map, players, map)
    }

    fun calculate(
        map: List<MapPosition>,
        players: List<PlayerPosition>,
        spawnPositions: List<MapPosition>
    ): Array<MapPosition> {
        var sortedMap = map.sortedWith(compareBy<MapPosition> { it.pos[0] }.thenBy { it.pos[1] })
        var sortedPlayers = players.sortedWith(compareBy<PlayerPosition> { it.pos[0] }.thenBy { it.pos[1] })
        var weighted: Array<MapPosition> = weightMap(sortedMap, sortedPlayers)
        val filtered = weighted.filter { existsInPositions(it.pos, spawnPositions) }
        return filtered.sortedBy { it.value }.toTypedArray()
    }

    private fun weightMap(
        map: List<MapPosition>,
        players: List<PlayerPosition>
    ): Array<MapPosition> {
        var pIndex = 0;
        var i = 0
        while(i < map.size) {
            if(players[pIndex].pos[0] == map[i].pos[0] &&
                players[pIndex].pos[1] == map[i].pos[1]) {
                var friendlyModifier = 1
                if(players[pIndex].friendly) friendlyModifier *= -1
                weightPositionByProximity(
                    map,
                    i,
                    WeightedValues.proximityWeight * friendlyModifier,
                    Direction.Source,
                    0
                )
                weightPositionByAngle(
                    map,
                    i,
                    WeightedValues.viewAngleWeight * friendlyModifier,
                    cleanAngle(players[pIndex].viewAngle)
                )
                pIndex++
            } else i++
            if(pIndex == players.size)
                break
        }
        return map.toTypedArray()
    }

    private enum class Direction {
        Source, Vertical, Horizontal
    }

    private fun weightPositionByProximity(
        map: List<MapPosition>,
        mapIndex: Int,
        remainingValue: Int,
        direction: Direction,
        directionValue: Int
    ) {
        if(remainingValue <= 0) return
        val pos = map[mapIndex]
        if(!pos.adjustable) return
        pos.value += remainingValue
        when(direction) {
            Direction.Source -> {
                val bottomIndex = getIndexFromPosition(map, arrayOf(pos.pos[0], pos.pos[1] - 1))
                val leftIndex = getIndexFromPosition(map, arrayOf(pos.pos[0] - 1, pos.pos[1]))
                val rightIndex = getIndexFromPosition(map, arrayOf(pos.pos[0] + 1, pos.pos[1]))
                val topIndex = getIndexFromPosition(map, arrayOf(pos.pos[0], pos.pos[1] + 1))
                if(bottomIndex != -1)
                    weightPositionByProximity(
                        map,
                        bottomIndex,
                        remainingValue - 1,
                        Direction.Vertical,
                        -1
                    )
                if(leftIndex != -1)
                    weightPositionByProximity(
                        map,
                        leftIndex,
                        remainingValue - 1,
                        Direction.Horizontal,
                        -1
                    )
                if(rightIndex != -1)
                    weightPositionByProximity(
                        map,
                        rightIndex,
                        remainingValue - 1,
                        Direction.Horizontal,
                        1
                    )
                if(topIndex != -1)
                    weightPositionByProximity(
                        map,
                        topIndex,
                        remainingValue - 1,
                        Direction.Vertical,
                        1
                    )
            }
            Direction.Vertical -> {
                val leftIndex = getIndexFromPosition(map, arrayOf(pos.pos[0] - 1, pos.pos[1]))
                val continueIndex = getIndexFromPosition(map,
                    arrayOf(pos.pos[0], pos.pos[1] + directionValue))
                val rightIndex = getIndexFromPosition(map, arrayOf(pos.pos[0] + 1, pos.pos[1]))
                if(leftIndex != -1)
                    weightPositionByProximity(
                        map,
                        leftIndex,
                        remainingValue - 1,
                        Direction.Horizontal,
                        -1
                    )
                if(continueIndex != -1)
                    weightPositionByProximity(
                        map,
                        continueIndex,
                        remainingValue - 1,
                        Direction.Vertical,
                        directionValue
                    )
                if(rightIndex != -1)
                    weightPositionByProximity(
                        map,
                        rightIndex,
                        remainingValue - 1,
                        Direction.Horizontal,
                        1
                    )
            }
            Direction.Horizontal -> {
                val nextIndex = getIndexFromPosition(map, arrayOf(pos.pos[0] + directionValue, pos.pos[1]))
                if(nextIndex != -1)
                    weightPositionByProximity(
                        map,
                        nextIndex,
                        remainingValue - 1,
                        direction,
                        directionValue
                    )
            }
        }
    }

    private fun getIndexFromPosition(map: List<MapPosition>, pos: Array<Int>): Int {
        for(i in 0..map.size) {
            if(map[i].pos[0] == pos[0])
                if(map[i].pos[1] == pos[1])
                    return i
                else if(map[i].pos[1] < pos[1])
                    return -1
            else if(map[i].pos[0] < pos[0])
                return -1
        }
        return -1
    }

    private fun weightPositionByAngle(
        map: List<MapPosition>,
        mapIndex: Int,
        remainingValue: Int,
        angle: Double
    ) {
        if(remainingValue <= 0) return
        val pos = map[mapIndex]
        if(!pos.adjustable) return
        pos.value += remainingValue
        val nextX = (-1*cos(angle) + pos.pos[0]).toInt()
        val nextY = (-1*sin(angle) + pos.pos[1]).toInt()
        val nextIndex = getIndexFromPosition(map, arrayOf(nextX, nextY))
        if(nextIndex != -1)
            weightPositionByAngle(map, nextIndex, remainingValue - 1, angle)
        for(i in 1..WeightedValues.viewAngleTolerance) {
            val tryIndex = getIndexFromPosition(map, arrayOf(nextX + i, nextY + i))
            if(tryIndex != -1)
                weightPositionByAngle(map, tryIndex, remainingValue - 1, angle)

            if(i != 1) {
                val tryIndex2 = getIndexFromPosition(map, arrayOf(nextX - i + 1, nextY - i + 1))
                if(tryIndex2 != -1)
                    weightPositionByAngle(map, tryIndex2, remainingValue - 1, angle)
            }
        }
    }

    private fun cleanAngle(angle: Double): Double {
        var cleaned = angle
        return if(cleaned >= 0) {
            while(cleaned > 1)
                cleaned -= 1
            cleaned
        } else {
            while(cleaned < -1)
                cleaned += 1
            100 - abs(cleaned)
        }
    }

    private fun existsInPositions(pos: Array<Int>, spawnPositions: List<MapPosition>): Boolean {
        spawnPositions.forEach {
            if(it.pos[0] == pos[0] && it.pos[1] == pos[1]) return true
        }
        return false
    }

}