package sdl.interpreter
import sdl.ast._
import sdl.util._
import scala.lms.common._
import scala.reflect.SourceContext
import java.io.File
import java.util.Scanner
import scala.collection.immutable.Nil
import scala.lms.api._
trait InterpreterUtil extends DslExp with TableUtil with ProgramUtil {
  implicit def anyTyp: Typ[Any] = manifestTyp
  class Interpreter(program: Program) {
    val DEBUG = false
    type Env = Map[RelId, Decl]
    val initial = Map[Id, Tuple]()
    def run() = {
      val env = program.env
      val indices = program.collectIndices
      val tableManager = new TableManager(env, indices)

      program.stmts.foreach(evalStmt(_)(tableManager))
    }
    private def evalStmt(
        stmt: Stmt
    )(implicit tableManager: TableManager): Rep[Unit] = {
      if (DEBUG) println("running" + stmt.toString())
      stmt match {
        case LoadStmt(rel, filename)  => tableManager(rel).loadFrom(filename)
        case StoreStmt(rel, filename) => tableManager(rel).storeTo(filename)
        case ClearStmt(rel) => {
          // printf("start cleaning %s", rel)
          tableManager(rel).clear()
          // printf("end cleaning %s", rel)
        }
        case SwapStmt(relA, relB) =>
          Table.swap(tableManager(relA), tableManager(relB))
        case QueryStmt(op) => evalOp(op)(tableManager, initial)
        case LoopStmt(stmts) => {
          var flag = true: Rep[Boolean]
          def unroll(stmts: List[Stmt]) {
            stmts match {
              case head :: tl => {
                head match {
                  case ExitStmt(cond) =>
                    if (evalCond(cond)(tableManager, initial)) {
                      flag = false
                    } else {
                      unroll(tl)
                    }
                  case _ => {
                    evalStmt(head)
                    unroll(tl)
                  }
                }
              }
              case _ => ()
            }
          }
          // var i = 0: Rep[Int]
          while (flag) {
            // printf("LOOP COUNT: %d\\n", readVar(i))
            // i += 1
            unroll(stmts)
          }
        }
        case _ => throw new IllegalStateException("should not reach")
      }
    }
    private def evalOp(op: Op)(
        implicit tableManager: TableManager,
        env: Map[Id, Tuple]
    ): Rep[Unit] = {
      if (DEBUG) println("running" + op.toString())
      op match {
        case IndexScan(v, rel, indices, child) => {
          val fields = indices.toList.map(_._1)
          val values = indices.toList.map(_._2).map(evalExpr(_))
          tableManager(rel)(fields, values: _*).foreach { tup =>
            // printf(rel)
            // printf(" INDEX SCAN FIND TUPLE")
            // tup.foreach(ele => printf("%d ", ele))
            // printf("\\n")
            evalOp(child)(tableManager, env + (v -> tup))
          }
        }
        case Scan(v, rel, child) => {
          tableManager(rel).foreach { tup =>
            // printf(rel)
            // printf(" SCAN FIND TUPLE")
            // tup.foreach(ele => printf("%d ", ele))
            // printf("\\n")
            evalOp(child)(tableManager, env + (v -> tup))
          }
        }
        case IndexChoice(v, rel, cond, indices, child) => {
          val fields = indices.toList.map(_._1)
          val values = indices.toList.map(_._2).map(evalExpr(_))
          tableManager(rel)(fields, values: _*).stopableForeach { tup =>
            {
              val isSuccess =
                evalCond(cond)(tableManager, env + (v -> tup)): Rep[Boolean]
              if (isSuccess) {
                evalOp(child)(tableManager, env + (v -> tup))
              }
              !isSuccess
            }
          }
        }
        case Choice(v, rel, cond, child) => {
          tableManager(rel).stopableForeach { tup =>
            {
              val isSuccess =
                evalCond(cond)(tableManager, env + (v -> tup)): Rep[Boolean]
              if (isSuccess) {
                evalOp(child)(tableManager, env + (v -> tup))
              }
              !isSuccess
            }
          }
        }
        case Filter(cond, child) => {

            val text = cond.toString
          if (evalCond(cond)) {
            // printf("%s TRUE\\n", text)
            evalOp(child)
          } else {
            // printf("%s FALSE\\n", text)
          }
        }
        case Project(rel, exprs) => {
          tableManager(rel).push(exprs.map(evalExpr(_)): _*)
        }
      }
    }
    private def evalCond(
        cond: Cond
    )(
        implicit tableManager: TableManager,
        env: Map[Id, Tuple]
    ): Rep[Boolean] = {
      if (DEBUG) println("running" + cond.toString())
      cond match {
        case Conjunction(lhs, rhs) => evalCond(lhs) && evalCond(rhs)
        case Negation(child)       => !evalCond(child)
        case Constraint(lhs, op, rhs) => {
          op match {
            case CstraintOp.EQ => evalExpr(lhs) == evalExpr(rhs)
            // I know they are ugly, but...
            case CstraintOp.LT =>
              evalExpr(lhs).asInstanceOf[Rep[Int]] < evalExpr(rhs)
                .asInstanceOf[Rep[Int]]
            case CstraintOp.LE =>
              evalExpr(lhs).asInstanceOf[Rep[Int]] <= evalExpr(rhs)
                .asInstanceOf[Rep[Int]]
            case CstraintOp.GT =>
              evalExpr(lhs).asInstanceOf[Rep[Int]] > evalExpr(rhs)
                .asInstanceOf[Rep[Int]]
            case CstraintOp.GE =>
              evalExpr(lhs).asInstanceOf[Rep[Int]] >= evalExpr(rhs)
                .asInstanceOf[Rep[Int]]
            case _ => throw new IllegalStateException("shouldn't reached")
          }
          // assert(op == CstraintOp.EQ)
          // evalExpr(lhs) == evalExpr(rhs)
        }
        case DoesExist(exprs, rel) => {
          val fields = exprs.zipWithIndex.filter(_._1 != Bot).map {
            case (expr, idx) => {
              tableManager(rel).schema(idx)._1 // field
            }
          }
          val values = exprs.filter(_ != Bot).map((evalExpr(_)))
          val result = var_new(false)
          tableManager(rel)(fields, values: _*).stopableForeach(_ => {
            result = true
            false
          })
          result
        }
        case IsEmpty(rel) => {
          tableManager(rel).isEmpty
        }
      }
    }
    private def evalExpr(
        expr: Expr
    )(implicit tableManager: TableManager, env: Map[Id, Tuple]): Rep[Any] = {
      if (DEBUG) println("running" + expr.toString())
      expr match {
        case TupleElement(id, elem) => env(id)(elem)
        case ConstValue(value) =>
          if (value.isInstanceOf[Int]) unit(value.asInstanceOf[Int])
          else unit(value.asInstanceOf[String])
        case BinaryExpr(lhs, op, rhs) => {
          val lval = evalExpr(lhs).asInstanceOf[Rep[Int]]
          val rval = evalExpr(rhs).asInstanceOf[Rep[Int]]
          op match {
            case ExprOp.ADD => lval + rval
            case ExprOp.SUB => lval - rval
            case ExprOp.MUL => lval * rval
            case ExprOp.DIV => lval / rval
          }
        }
        case Bot => throw new IllegalStateException("should not be reached")
      }
    }
  }
}
