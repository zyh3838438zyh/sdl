import scala.lms.api._
import sdl.staging._
import sdl.interpreter._
import scala.lms.common._
import scala.lms.internal.Config
import sdl.parser.ParserUtil

object Main {

  def main(args: Array[String]): Unit = {
    def snippet =
      new DslDriverC[String, Unit] with InterpreterUtil with ParserUtil {
        // verbosity = 100
        // sourceinfo = 100
        def readFile(filename: String): String = {
          val source = scala.io.Source.fromFile(filename)
          try source.getLines mkString "\n"
          finally source.close()
        }

        def parseProgram(prog: String): Program =
          DlParser.parseAll(DlParser.program, prog).get

        override def snippet(unused: Rep[String]) = {
          // val text = readFile("test/cprog1/cprog1.ram")
          val text = readFile("test/cprog5/cprog5.ram")
          // val text = readFile("test/tc/tc.ram")
          // val text = readFile("test/cellular_automata/cellular_automata.ram")
          // val text = readFile("test/tak/tak.ram")
          // val text = readFile("test/topological_order/topological_order.ram")
          val prog = parseProgram(text)
          new Interpreter(prog).run()
        }
      }
    // val out = new java.io.PrintWriter("test/topological_order/test.c")
    // val out = new java.io.PrintWriter("test/cprog1/test.c")
    val out = new java.io.PrintWriter("test/cprog5/test.c")
    // val out = new java.io.PrintWriter("test/tc/test.c")
    // val out = new java.io.PrintWriter("test/tak/test.c")
    // val out = new java.io.PrintWriter("test/cellular_automata/test.c")
    out.println(snippet.code)
    out.close
    // System.out.println(snippet.code)
    // snippet.eval("1")
  }
}
