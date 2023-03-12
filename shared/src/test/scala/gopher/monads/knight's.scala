import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions
import scala.language.async

object KnightsTour {

  case class Position(x: Int, y: Int)

  type Board = List[Position]

  // Returns all valid moves that can be made from a given position on the board
  def getMoves(position: Position, board: Board): Try[List[Position]] = Try {
    val x = position.x
    val y = position.y
    val potentialMoves = List(
      Position(x + 2, y + 1),
      Position(x + 1, y + 2),
      Position(x - 1, y + 2),
      Position(x - 2, y + 1),
      Position(x - 2, y - 1),
      Position(x - 1, y - 2),
      Position(x + 1, y - 2),
      Position(x + 2, y - 1)
    )
    potentialMoves.filter(move => isValidMove(move, board))
  }

  // Checks if a given move is valid on the board
  def isValidMove(move: Position, board: Board): Boolean = {
    val x = move.x
    val y = move.y
    x >= 0 && x < 8 && y >= 0 && y < 8 && !board.contains(move)
  }

  // Finds a valid knight's tour on the board starting from a given position
  def findTour(position: Position, board: Board): Future[Board] = async {
    if (board.size == 64) {
      // All squares have been visited, so the tour is complete
      board
    } else {
      val nextMoves = await(getMoves(position, board).toFuture)
      for {
        move <- nextMoves
        tour <- findTour(move, move :: board)
      } yield tour
    }
  }

  // Finds any valid knight's tour on the board starting from any position
  def findAnyTour: Future[Board] = async {
    val position = Position(0, 0)
    val board = List(position)
    val tour = await(findTour(position, board))
    tour.reverse
  }

  def main(args: Array[String]): Unit = {
    findAnyTour.onComplete {
      case Success(tour) =>
        println("Found a knight's tour:")
        tour.foreach(position => println(s"${position.x},${position.y}"))
      case Failure(exception) =>
        println(s"Failed to find a knight's tour: ${exception.getMessage}")
    }
  }

}
