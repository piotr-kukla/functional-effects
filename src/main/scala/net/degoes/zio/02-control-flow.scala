package net.degoes.zio

import zio._
import scala.collection.immutable.Nil
import scala.annotation.tailrec

object Looping extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement a `repeat` combinator using `flatMap` (or `zipRight`) and recursion.
   */
  def repeat[R, E, A](n: Int)(effect: ZIO[R, E, A]): ZIO[R, E, Chunk[A]] =
    if (n == 0) ZIO.succeed(Chunk.empty)
    else for {
        a <- effect
        chunkA <- repeat(n-1)(effect)
      } yield Chunk(a) ++ chunkA

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    repeat(100)(putStrLn("All work and no play makes Jack a dull boy")).exitCode
}

object Interview extends App {
  import java.io.IOException
  import zio.console._

  val questions =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Implement the `getAllAnswers` function in such a fashion that it will ask
   * the user each question and collect them all into a list.
   */
  def getAllAnswers(questions: List[String]): ZIO[Console, IOException, List[String]] =
    questions match {
      case Nil     => ZIO.succeed(List())
      case q :: qs => for {
        _ <- putStrLn(q)
        answer <- getStrLn
        answers <- getAllAnswers(qs)
      } yield answer::answers
    }

  /**
   * EXERCISE
   *
   * Use the preceding `getAllAnswers` function, together with the predefined
   * `questions`, to ask the user a bunch of questions, and print the answers.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      answers <- getAllAnswers(questions)
      _       <- putStrLn("Your answers: ")
      _       <- putStrLn(answers.mkString("\n"))
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object InterviewGeneric extends App {
  import java.io.IOException
  import zio.console._

  val questions =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Implement the `iterateAndCollect` function.
   */
  def iterateAndCollect[R, E, A, B](as: List[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    as match {
      case Nil     => ZIO.succeed(Nil)
      case a :: as => for {
        b  <- f(a)
        bs <- iterateAndCollect(as)(f)
      } yield b :: bs
    }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      answers <- iterateAndCollect(questions)(q => putStrLn(q) *> getStrLn)
      _       <- putStrLn("Your answers: ")
      _       <- putStrLn(answers.mkString("\n"))
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object InterviewForeach extends App {
  import zio.console._

  val questions =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Using `ZIO.foreach`, iterate over each question in `questions`, print the
   * question to the user (`putStrLn`), read the answer from the user
   * (`getStrLn`), and collect all answers into a collection. Finally, print
   * out the contents of the collection.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      answers <- ZIO.foreach(questions)(q => putStrLn(q) *> getStrLn)
      _       <- putStrLn("Your answers: ")
      _       <- putStrLn(answers.mkString("\n"))
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object WhileLoop extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement the functional effect version of a while loop.
   */
  def whileLoop[R, E, A](cond: UIO[Boolean])(zio: ZIO[R, E, A]): ZIO[R, E, Chunk[A]] =
    cond.flatMap(condV => {
      if (condV) for {
        a <- zio
        as <- whileLoop(cond)(zio)
       } yield Chunk(a) ++ as
      else ZIO.succeed(Chunk.empty)
    })

  def whileLoop2[R, E, A](cond: UIO[Boolean])(zio: ZIO[R, E, A]): ZIO[R, E, Chunk[A]] =
    cond.flatMap(condV => {
      if (condV) ((zio zipWith whileLoop2(cond)(zio)){ case (a, as) => Chunk(a) ++ as})
      else ZIO.succeed(Chunk.empty)
    })


  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    def loop(variable: Ref[Int]) =
      whileLoop2(variable.get.map(_ < 100)) {
        for {
          value <- variable.get
          _     <- putStrLn(s"At iteration: ${value}")
          _     <- variable.update(_ + 1)
        } yield ()
      }

    (for {
      variable <- Ref.make(0)
      _        <- loop(variable)
    } yield 0).exitCode
  }
}

object Iterate extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement the `iterate` function such that it iterates until the condition
   * evaluates to false, returning the "last" value of type `A`.
   */
  def iterate[R, E, A](start: A)(cond: A => Boolean)(f: A => ZIO[R, E, A]): ZIO[R, E, A] =
    ???

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    iterate(0)(_ < 100) { i =>
      putStrLn(s"At iteration: ${i}") as (i + 1)
    }.exitCode
}

object TailRecursive extends App {
  import zio.duration._

  trait Response
  trait Request {
    def returnResponse(response: Response): Task[Unit]
  }

  lazy val acceptRequest: Task[Request] = Task(new Request {
    def returnResponse(response: Response): Task[Unit] =
      Task(println(s"Returning response ${response}"))
  })

  def handleRequest(request: Request): Task[Response] = Task {
    println(s"Handling request ${request}")
    new Response {}
  }

  /**
   * EXERCISE
   *
   * Make this infinite loop (which represents a webserver) effectfully tail
   * recursive.
   */
  lazy val webserver: Task[Nothing] =
    for {
      request  <- acceptRequest
      response <- handleRequest(request)
      _        <- request.returnResponse(response)
      nothing  <- webserver
    } yield nothing

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      fiber <- webserver.fork
      _     <- ZIO.sleep(100.millis)
      _     <- fiber.interrupt
    } yield ()).exitCode
}
