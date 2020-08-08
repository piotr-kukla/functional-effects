package net.degoes.zio

import zio._

object ForkJoin extends App {
  import zio.console._

  val printer: ZIO[Console, Nothing, Int] =
    putStrLn(".").repeat(Schedule.recurs(10))

  /**
   * EXERCISE
   *
   * Using `ZIO#fork`, fork the `printer` into a separate fiber, and then
   * print out a message, "Forked", then join the fiber using `Fiber#join`,
   * and finally, print out a message "Joined".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for  {
      fiber <- printer.fork
      _     <- putStrLn("Forked")
      _     <- fiber.join
      _     <- putStrLn("Joined")
    } yield ExitCode.success
}

object ForkInterrupt extends App {
  import zio.console._
  import zio.duration._

  val infinitePrinter =
    putStrLn(".").forever

  /**
   * EXERCISE
   *
   * Using `ZIO#fork`, fork the `printer` into a separate fiber, and then
   * print out a message, "Forked", then using `ZIO.sleep`, sleep for 100
   * milliseconds, then interrupt the fiber using `Fiber#interrupt`, and
   * finally, print out a message "Interrupted".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      fiber <- infinitePrinter.fork
      _     <- putStrLn("Forked")
      _     <- ZIO.sleep(100.millis)
      _     <- fiber.interrupt
      _     <- putStrLn("Interrupted")
    } yield ExitCode.success
}

object ParallelFib extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Rewrite this implementation to compute nth fibonacci number in parallel.
   */
  def fib(n: Int): UIO[BigInt] = {
    def loop(n: Int, original: Int): UIO[BigInt] =
      if (n <= 1) UIO(n)
      else
        UIO.effectSuspendTotal {
          (loop(n - 1, original) zipWithPar loop(n - 2, original))(_ + _)
        }

    loop(n, n)
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      _ <- putStrLn(
            "What number of the fibonacci sequence should we calculate?"
          )
      n <- getStrLn.orDie.flatMap(input => ZIO(input.toInt)).eventually
      f <- fib(n)
      _ <- putStrLn(s"fib(${n}) = ${f}")
    } yield ExitCode.success
}

object AlarmAppImproved extends App {
  import zio.console._
  import zio.duration._
  import java.io.IOException
  import java.util.concurrent.TimeUnit

  lazy val getAlarmDuration: ZIO[Console, IOException, Duration] = {
    def parseDuration(input: String): IO[NumberFormatException, Duration] =
      ZIO
        .effect(
          Duration((input.toDouble * 1000.0).toLong, TimeUnit.MILLISECONDS)
        )
        .refineToOrDie[NumberFormatException]

    val fallback = putStrLn("You didn't enter a number of seconds!") *> getAlarmDuration

    for {
      _        <- putStrLn("Please enter the number of seconds to sleep: ")
      input    <- getStrLn
      duration <- parseDuration(input) orElse fallback
    } yield duration
  }

  /**
   * EXERCISE
   *
   * Create a program that asks the user for a number of seconds to sleep,
   * sleeps the specified number of seconds using ZIO.sleep(d), concurrently
   * prints a dot every second that the alarm is sleeping for, and then
   * prints out a wakeup alarm message, like "Time to wakeup!!!".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      duration <- getAlarmDuration
      fiber    <- (putStr(".") *> ZIO.sleep(1 second)).forever.fork
      _        <- ZIO.sleep(duration)
      _        <- fiber.interrupt
      _        <- putStrLn("Time to wakeup!!!")
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object ComputePi extends App {
  import zio.random._
  import zio.console._
  import zio.clock._
  import zio.duration._
  import zio.stm._

  /**
   * Some state to keep track of all points inside a circle,
   * and total number of points.
   */
  final case class PiState(
    inside: Ref[Long],
    total: Ref[Long]
  )

  /**
   * A function to estimate pi.
   */
  def estimatePi(inside: Long, total: Long): Double =
    (inside.toDouble / total.toDouble) * 4.0

  /**
   * A helper function that determines if a point lies in
   * a circle of 1 radius.
   */
  def insideCircle(x: Double, y: Double): Boolean =
    Math.sqrt(x * x + y * y) <= 1.0

  /**
   * An effect that computes a random (x, y) point.
   */
  val randomPoint: ZIO[Random, Nothing, (Double, Double)] =
    nextDouble zip nextDouble

  def oneEstimation(piState: PiState): ZIO[Console with Random, Nothing, Unit] =
    for {
      rp <- randomPoint
      _  <- piState.total.update(_ + 1)
      _  <- if (insideCircle(rp._1, rp._2)) piState.inside.update(_ + 1) else ZIO.succeed(())
//      inside <- piState.inside.get
//      total <- piState.total.get
//      fiberId <- ZIO.fiberId
//      _  <- putStrLn(s"Estimate PI: ${estimatePi(inside, total)} on fiberId: $fiberId")
    } yield ()

  /**
   * EXERCISE
   *
   * Build a multi-fiber program that estimates the value of `pi`. Print out
   * ongoing estimates continuously until the estimation is complete.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      insideRef <- Ref.make(0L)
      totalRef  <- Ref.make(0L)
      piState = PiState(insideRef, totalRef)
      worker = oneEstimation(piState).forever
      workers = List.fill(3)(worker)
      fiber1 <- ZIO.forkAll(workers)
      //fiber2 <- (putStrLn(s"Estimate PI: ${estimatePi(insideRef.get, total)}))
      _      <- ZIO.sleep(3 second)
      _      <- fiber1.interrupt
      inside <- insideRef.get
      total  <- totalRef.get
      _      <- putStrLn(s"Estimate PI: ${estimatePi(inside, total)}")
    } yield ExitCode.success
}

object ParallelZip extends App {
  import zio.console._

  def fib(n: Int): UIO[Int] =
    if (n <= 1) UIO(n)
    else
      UIO.effectSuspendTotal {
        (fib(n - 1) zipWith fib(n - 2))(_ + _)
      }

  /**
   * EXERCISE
   *
   * Compute fib(10) and fib(13) in parallel using `ZIO#zipPar`, and display
   * the result.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (fib(10) zipPar fib(13)).flatMap(t => putStrLn(t.toString)).exitCode
}

object StmSwap extends App {
  import zio.console._
  import zio.stm._

  /**
   * EXERCISE
   *
   * Demonstrate the following code does not reliably swap two values in the
   * presence of concurrency.
   */
  def exampleRef: UIO[Int] = {
    def swap[A](ref1: Ref[A], ref2: Ref[A]): UIO[Unit] =
      for {
        v1 <- ref1.get
        v2 <- ref2.get
        _  <- ref2.set(v1)
        _  <- ref1.set(v2)
      } yield ()

    for {
      ref1   <- Ref.make(100)
      ref2   <- Ref.make(0)
      fiber1 <- swap(ref1, ref2).repeat(Schedule.recurs(100)).fork
      fiber2 <- swap(ref2, ref1).repeat(Schedule.recurs(100)).fork
      _      <- (fiber1 zip fiber2).join
      value  <- (ref1.get zipWith ref2.get)(_ + _)
    } yield value
  }

  /**
   * EXERCISE
   *
   * Using `STM`, implement a safe version of the swap function.
   */
  def exampleStm: UIO[Int] = {
    def swap[A](ref1: TRef[A], ref2: TRef[A]): STM[Nothing, Unit] =
      for {
        v1 <- ref1.get
        v2 <- ref2.get
        _  <- ref2.set(v1)
        _  <- ref1.set(v2)
      } yield ()

    for {
      ref1 <- STM.atomically(TRef.make(100))
      ref2 <- STM.atomically(TRef.make(0))
      fiber1 <- STM.atomically(swap(ref1, ref2)).repeat(Schedule.recurs(100)).fork
      fiber2 <- STM.atomically(swap(ref2, ref1)).repeat(Schedule.recurs(100)).fork
      _      <- (fiber1 zip fiber2).join
      value  <- STM.atomically((ref1.get zipWith ref2.get)(_ + _))
    } yield value
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    //exampleRef.map(_.toString).flatMap(putStrLn(_)).exitCode
    exampleStm.map(_.toString).flatMap(putStrLn(_)).exitCode
}

object StmLock extends App {
  import zio.console._
  import zio.stm._
  import zio.duration._

  /**
   * EXERCISE
   *
   * Using STM, implement a simple binary lock by implementing the creation,
   * acquisition, and release methods.
   */
  class Lock private (tref: TRef[Boolean]) {
    def acquire: UIO[Unit] = STM.atomically(tref.get.retryUntil(_ == false) *> tref.set(true))
    def release: UIO[Unit] = STM.atomically(tref.set(false))
  }
  object Lock {
    def make: UIO[Lock] = STM.atomically(
      for {
        ref <- TRef.make(false)
      } yield new Lock(ref)
    )
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      lock <- Lock.make
      fiber1 <- lock.acquire
                 .bracket_(lock.release)(putStrLn("Bob  : I have the lock!") *> ZIO.sleep(1 second))
                 .repeat(Schedule.recurs(10))
                 .fork
      fiber2 <- lock.acquire
                 .bracket_(lock.release)(putStrLn("Sarah: I have the lock!") *> ZIO.sleep(1 second))
                 .repeat(Schedule.recurs(10))
                 .fork
      _ <- (fiber1 zip fiber2).join
    } yield ExitCode.success
}

object StmQueue extends App {
  import zio.console._
  import zio.stm._
  import scala.collection.immutable.{ Queue => ScalaQueue }

  /**
   * EXERCISE
   *
   * Using STM, implement a async queue with double back-pressuring.
   */
  class Queue[A] private (capacity: Int, queue: TRef[ScalaQueue[A]]) {
    def take: UIO[A]           = STM.atomically(
      for {
        _            <- queue.get.retryUntil(!_.isEmpty)
        (head, tail) <- queue.get.map(_.dequeue)
        _            <- queue.set(tail)
      } yield head

    )
    def offer(a: A): UIO[Unit] = STM.atomically(queue.get.retryUntil(_.size < capacity) *> queue.update(_.enqueue(a)))
  }
  object Queue {
    def bounded[A](capacity: Int): UIO[Queue[A]] = STM.atomically(
      for {
        queue <- TRef.make(ScalaQueue[A]())
      } yield new Queue(capacity, queue)
    )
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      queue <- Queue.bounded[Int](10)
      _     <- ZIO.foreach(0 to 100)(i => queue.offer(i)).fork
      _ <- ZIO.foreach(0 to 100)(
            _ => queue.take.flatMap(i => putStrLn(s"Got: ${i}"))
          )
    } yield ExitCode.success
}

object StmLunchTime extends App {
  import zio.console._
  import zio.stm._
  import zio.duration._

  /**
   * EXERCISE
   *
   * Using STM, implement the missing methods of Attendee.
   */
  final case class Attendee(state: TRef[Attendee.State], name: String) {
    import Attendee.State._

    def isStarving: STM[Nothing, Boolean] = state.get.map(_ == Starving)

    def feed: STM[Nothing, Unit] = state.set(Full)
  }
  object Attendee {
    sealed trait State
    object State {
      case object Starving extends State
      case object Full     extends State
    }
  }

  /**
   * EXERCISE
   *
   * Using STM, implement the missing methods of Table.
   */
  final case class Table(seats: TArray[Boolean]) {
    def findEmptySeat: STM[Nothing, Option[Int]] =
      seats
        .fold[(Int, Option[Int])]((0, None)) {
          case ((index, z @ Some(_)), _) => (index + 1, z)
          case ((index, None), taken) =>
            (index + 1, if (taken) None else Some(index))
        }
        .map(_._2)

    def takeSeat(index: Int): STM[Nothing, Unit] = seats.update(index, _ => true)

    def vacateSeat(index: Int): STM[Nothing, Unit] = seats.update(index, _ => false)
  }

  /**
   * EXERCISE
   *
   * Using STM, implement a method that feeds a single attendee.
   */
  def feedAttendee(t: Table, a: Attendee): STM[Nothing, Int] =

    for {
      index <- t.findEmptySeat.retryUntil(_.isDefined).map(_.get)
      //_     <- STM.succeed(println(s"${a.name}: taking place: $index"))
      _     <- t.takeSeat(index)
      //_     <- t.vacateSeat(index)
      //_     <- STM.succeed(println(s"${a.name}: vacating place: $index"))
    } yield index

  /**
   * EXERCISE
   *
   * Using STM, implement a method that feeds only the starving attendees.
   */
  def feedStarving(table: Table, list: List[Attendee]): ZIO[Console with clock.Clock, Nothing, Unit] =
    //STM.foreach(list)(a => feedAttendee(table, a)).commit.as(())
  ZIO.foreachPar(list) (a =>
    for {
      _     <- putStrLn(s"${a.name}: getting into Restaurant")
      index <- STM.atomically(feedAttendee(table, a))
      _     <- putStrLn(s"${a.name}: eating at table: $index")
      _     <- ZIO.sleep(2 seconds)
      _     <- table.vacateSeat(index).commit
      _     <- putStrLn(s"${a.name}: exit.")
    } yield ()
  ).as(())



  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val Attendees = 15
    val TableSize = 5

    for {
      attendees <- ZIO.foreach(1 to Attendees)(
                    i =>
                      TRef
                        .make[Attendee.State](Attendee.State.Starving)
                        .map(state => Attendee(state, s"Smith${i}"))
                        .commit
                  )
      table <- TArray
                .fromIterable(List.fill(TableSize)(false))
                .map(Table(_))
                .commit
      _ <- feedStarving(table, attendees)
      //_    <- putStrLn(seatsIdx.mkString(","))
    } yield ExitCode.success
  }
}

object StmPriorityQueue extends App {
  import zio.console._
  import zio.stm._
  import zio.duration._

  /**
   * EXERCISE
   *
   * Using STM, design a priority queue, where smaller integers are assumed
   * to have higher priority than greater integers.
   */
  class PriorityQueue[A] private (
    minLevel: TRef[Option[Int]],
    map: TMap[Int, TQueue[A]]
  ) {
    def offer(a: A, priority: Int): STM[Nothing, Unit] = ???

    def take: STM[Nothing, A] = ???
  }
  object PriorityQueue {
    def make[A]: STM[Nothing, PriorityQueue[A]] = ???
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _     <- putStrLn("Enter any key to exit...")
      queue <- PriorityQueue.make[String].commit
      lowPriority = ZIO.foreach(0 to 100) { i =>
        ZIO.sleep(1.millis) *> queue
          .offer(s"Offer: ${i} with priority 3", 3)
          .commit
      }
      highPriority = ZIO.foreach(0 to 100) { i =>
        ZIO.sleep(2.millis) *> queue
          .offer(s"Offer: ${i} with priority 0", 0)
          .commit
      }
      _ <- ZIO.forkAll(List(lowPriority, highPriority)) *> queue.take.commit
            .flatMap(putStrLn(_))
            .forever
            .fork *>
            getStrLn
    } yield 0).exitCode
}

object StmReentrantLock extends App {
  import zio.console._
  import zio.stm._

  private final case class WriteLock(
    writeCount: Int,
    readCount: Int,
    fiberId: Fiber.Id
  )
  private final class ReadLock private (readers: Map[Fiber.Id, Int]) {
    def total: Int = readers.values.sum

    def noOtherHolder(fiberId: Fiber.Id): Boolean =
      readers.size == 0 || (readers.size == 1 && readers.contains(fiberId))

    def readLocks(fiberId: Fiber.Id): Int =
      readers.get(fiberId).fold(0)(identity)

    def adjust(fiberId: Fiber.Id, adjust: Int): ReadLock = {
      val total = readLocks(fiberId)

      val newTotal = total + adjust

      new ReadLock(
        readers =
          if (newTotal == 0) readers - fiberId
          else readers.updated(fiberId, newTotal)
      )
    }
  }
  private object ReadLock {
    val empty: ReadLock = new ReadLock(Map())

    def apply(fiberId: Fiber.Id, count: Int): ReadLock =
      if (count <= 0) empty else new ReadLock(Map(fiberId -> count))
  }

  /**
   * EXERCISE
   *
   * Using STM, implement a reentrant read/write lock.
   */
  class ReentrantReadWriteLock(data: TRef[Either[ReadLock, WriteLock]]) {
    def writeLocks: UIO[Int] = ???

    def writeLocked: UIO[Boolean] = ???

    def readLocks: UIO[Int] = ???

    def readLocked: UIO[Boolean] = ???

    val read: Managed[Nothing, Int] = ???

    val write: Managed[Nothing, Int] = ???
  }
  object ReentrantReadWriteLock {
    def make: UIO[ReentrantReadWriteLock] = ???
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = ???
}

object StmDiningPhilosophers extends App {
  import zio.console._
  import zio.stm._

  sealed trait Fork
  val Fork = new Fork {}

  final case class Placement(
    left: TRef[Option[Fork]],
    right: TRef[Option[Fork]]
  )

  final case class Roundtable(seats: Vector[Placement])

  /**
   * EXERCISE
   *
   * Using STM, implement the logic of a philosopher to take not one fork, but
   * both forks when they are both available.
   */
  def takeForks(
    left: TRef[Option[Fork]],
    right: TRef[Option[Fork]]
  ): STM[Nothing, (Fork, Fork)] =
    ???

  /**
   * EXERCISE
   *
   * Using STM, implement the logic of a philosopher to release both forks.
   */
  def putForks(left: TRef[Option[Fork]], right: TRef[Option[Fork]])(
    tuple: (Fork, Fork)
  ): STM[Nothing, Unit] = ???

  def setupTable(size: Int): ZIO[Any, Nothing, Roundtable] = {
    val makeFork = TRef.make[Option[Fork]](Some(Fork))

    (for {
      allForks0 <- STM.foreach(0 to size) { i =>
                    makeFork
                  }
      allForks = allForks0 ++ List(allForks0(0))
      placements = (allForks zip allForks.drop(1)).map {
        case (l, r) => Placement(l, r)
      }
    } yield Roundtable(placements.toVector)).commit
  }

  def eat(
    philosopher: Int,
    roundtable: Roundtable
  ): ZIO[Console, Nothing, Unit] = {
    val placement = roundtable.seats(philosopher)

    val left  = placement.left
    val right = placement.right

    for {
      forks <- takeForks(left, right).commit
      _     <- putStrLn(s"Philosopher ${philosopher} eating...")
      _     <- putForks(left, right)(forks).commit
      _     <- putStrLn(s"Philosopher ${philosopher} is done eating")
    } yield ()
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val count = 10

    def eaters(table: Roundtable): Iterable[ZIO[Console, Nothing, Unit]] =
      (0 to count).map { index =>
        eat(index, table)
      }

    for {
      table <- setupTable(count)
      fiber <- ZIO.forkAll(eaters(table))
      _     <- fiber.join
      _     <- putStrLn("All philosophers have eaten!")
    } yield ExitCode.success
  }
}
