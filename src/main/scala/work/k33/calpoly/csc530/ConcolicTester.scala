package work.k33.calpoly.csc530

import java.nio.file.{Files, Paths}

import work.k33.calpoly.csc530.mini.MiniInterpreter
import work.k33.calpoly.csc530.mini.ast.{MiniAST, Program}
import work.k33.calpoly.csc530.mini.ast.statement.Statement
import work.k33.calpoly.csc530.pact._

import scala.Console.{GREEN, RED, RESET}
import scala.collection.mutable

case class Input(inputs: Map[Int, Int], bound: Int)

object ConcolicTester extends App {
  override def main(args: Array[String]): Unit = {
    args match {
      case Array(filename) =>
        testFile(filename, None)
      case Array(filename, iterationsStr) =>
        testFile(filename, Some(iterationsStr.toInt))
      case _ =>
        println("usage: scala ConcolicTester <filename> [max-iterations]")
    }
  }

  private def testFile(filename: String, maxIterations: Option[Int]): Unit = {
    if (filename.endsWith(".pact"))
      new ConcolicTester(PactInterpreter).test(fileToString(filename), maxIterations)
    else if (filename.endsWith(".mini"))
      new ConcolicTester(MiniInterpreter).gatherInputs(fileToString(filename), maxIterations, mutate)
    else
      println("unsupported file type: file must end with .pact or .mini")
  }

  private def mutate(miniAST: MiniAST): MiniAST = {
    miniAST match {
      case p: Program => {
        p.funcs.head.body.statements.map{case r => r}
        p
      }
      case o => o
    }
  }

  private def fileToString(filename: String): String = {
    new String(Files.readAllBytes(Paths.get(filename)))
  }
}

class ConcolicTester[T](interpreter: Interpreter[T]) {
  /**
    *
    * @param ast           The AST of the program to test.
    * @param maxIterations The maximum number of iterations to run
    * @param f             A function to run on each intermediate result. Can be used to print output if desired.
    * @return number of times the program was run
    */
  def test[A](ast: T, maxIterations: Option[Int], f: Result[T] => A): Seq[A] = {
    val results = new ArrayBuffer[A]
    var iterations = 0
    val workList: mutable.Queue[Input] = mutable.Queue(Input(Map(), 1))
    while (workList.nonEmpty && maxIterations.forall(iterations < _)) {
      val input = workList.dequeue()
      val res = interpreter.execute(ast, new ConcolicInputProvider(input.inputs))
      results += f(res)
      val Result(_, fullConstraints, numSymbols, _, _) = res
      // Remove unneeded constraints
      val constraints = fullConstraints.filter {
        case BoolS(_) => false
        case _ => true
      }
      val start = constraints.takeRight(input.bound - 1)
      val solver = new ConstraintSolver(start, numSymbols)
      var lastHead: Option[SymbolicBool] = None
      for (i <- input.bound to constraints.size) {
        val pc = constraints.takeRight(i)
        if (lastHead.isDefined) {
          solver.add(List(lastHead.get))
        }
        lastHead = Some(pc.head)
        solver.push()
        solver.add(List(NotS(pc.head)))
        solver.solve().foreach(
          newInput => workList.enqueue(Input(newInput, i + 1)))
        solver.pop()
      }
      solver.close()
      iterations += 1
    }
    results
  }

  def gatherInputs(program: String, maxIterations: Option[Int], mutate: T => T): Unit = {
    val ast = interpreter.parse(program)
    val retFunc = (r: Result[T]) => {
      r.result match {
        case Left(_) => None
        case Right(_) => Some(r.inputs)
      }
    }

    val successInputs = test(ast, maxIterations, retFunc).filter(_.isDefined).map(_.get)

    val mutated = mutate(ast)
    for (input <- successInputs) {
      val inputMap = input.map(i => i -> i).toMap
      val res = interpreter.execute(mutated, new ConcolicInputProvider(inputMap))
      res.result match {
        case Left(errorMsg) => throw new RuntimeException("Failed on second use")
        case Right(res) => println(s"Inputs ${input.mkString("[", ", ", "]")}\n    ${Console.GREEN}Result: $res ${Console.RESET}")
      }
    }
  }

  def test(program: String, maxIterations: Option[Int]): Unit = {
    val ast = interpreter.parse(program)
    val maxCoverage = interpreter.gatherTerms(ast)
    val totalCoverage = mutable.Set[T]()

    def retFunc(r: Result[T]) = {
      val Result(result, _, _, coverage, inputs) = r
      totalCoverage ++= coverage
      val inputStr = inputs.mkString("[", ", ", "]")
      result match {
        case Left(errorMsg) => println(s"Inputs $inputStr:\n    ${Console.RED}$errorMsg${Console.RESET}")
        case Right(res) => println(s"Inputs $inputStr:\n    ${Console.GREEN}Result: $res ${Console.RESET}")
      }
    }

    val iterations = test(ast, maxIterations, retFunc)
    println(s"\nCoverage: ${totalCoverage.size}/${maxCoverage.size}")
    println(s"Iterations: $iterations\n")

    val coverableLines = maxCoverage.flatMap {
      case miniAst: MiniAST => Some(miniAst.lineNum)
      case _ => None
    }

    val nonCoveredLines = (maxCoverage -- totalCoverage).flatMap {
      case miniAst: MiniAST => Some(miniAst.lineNum)
      case _ => None
    }

    program.split("\n").zipWithIndex.foreach {
      case (line, idx) =>
        if (!coverableLines.contains(idx + 1)) {
          println(line)
        } else if (nonCoveredLines.contains(idx + 1)) {
          println(RED + line + RESET)
        } else {
          println(GREEN + line + RESET)
        }
    }
  }
}
