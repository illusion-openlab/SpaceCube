package tech.illusion.spacecube.game

class PieceBag(private val random: kotlin.random.Random = kotlin.random.Random) {
    private var bag: MutableList<PieceType> = mutableListOf()

    fun next(): PieceType {
        if (bag.isEmpty()) {
            bag = PieceType.values().toMutableList()
            bag.shuffle(random)
        }
        return bag.removeAt(bag.size - 1)
    }
}
