package sdl.ast
import scala.lms.api.Dsl
import sdl.util._

trait AstUtil extends Dsl {

  type Id = String
  type RelId = String
  type Field = String
  type Indices = List[(Field, Expr)]
  case class IndexSchema(rel: RelId, fields: List[Field])
  type Type = Type.Value
  object Type extends Enumeration {
    val NUM, STR = Value
  }
  type Schema = List[(Field, Type)]

  case class Decl(rel: RelId, schema: Schema)

  sealed abstract class Stmt {
    def acceptOp(visitor: Op => Unit) = ()
  }
  case class LoadStmt(rel: RelId, filename: String) extends Stmt
  case class StoreStmt(rel: RelId, filename: String) extends Stmt
  case class ClearStmt(rel: RelId) extends Stmt
  case class SwapStmt(relA: RelId, relB: RelId) extends Stmt
  case class QueryStmt(op: Op) extends Stmt {
    override def acceptOp(visitor: Op => Unit) {
      this.op.accept(visitor)
    }
  }
  case class LoopStmt(stmts: List[Stmt]) extends Stmt {
    override def acceptOp(visitor: Op => Unit) {
      this.stmts.foreach(_.acceptOp(visitor))
    }
  }
  case class ExitStmt(cond: Cond) extends Stmt

  sealed abstract class Op {
    def accept(visitor: Op => Unit) {
      visitor(this)
    }
    def acceptCond(expr: Cond => Unit)
  }
  sealed abstract class NestedOp extends Op {
    val child: Op
    override def accept(visitor: Op => Unit) {
      visitor(this)
      child.accept(visitor)
    }
    override def acceptCond(visitor: Cond => Unit) {
      acceptCondInternal(visitor)
      child.acceptCond(visitor)
    }
    def acceptCondInternal(visitor: Cond => Unit)
  }
  sealed trait IndexedOp extends Op {
    val indices: Indices
    val indexedOn: RelId
  }
  sealed abstract class AbstractScan extends NestedOp {
    val v: Id
    val rel: RelId
    override def acceptCondInternal(visitor: Cond => Unit): Unit = ()
  }
  case class IndexScan(v: Id, rel: RelId, indices: Indices, child: Op)
      extends AbstractScan
      with IndexedOp {
    override val indexedOn = rel
  }
  case class Scan(v: Id, rel: RelId, child: Op) extends AbstractScan {
  }

  sealed abstract class AbstractChoice extends NestedOp {
    val v: Id
    val rel: RelId
    val cond: Cond
    override def acceptCondInternal(visitor: Cond => Unit) {
      cond.acceptCond(visitor)
    }
  }
  case class IndexChoice(
      v: Id,
      rel: RelId,
      cond: Cond,
      indices: Indices,
      child: Op
  ) extends AbstractChoice
      with IndexedOp {
    override val indexedOn: RelId = rel
  }
  case class Choice(v: Id, rel: RelId, cond: Cond, child: Op)
      extends AbstractChoice

  case class Filter(cond: Cond, child: Op) extends NestedOp {
    override def acceptCondInternal(visitor: Cond => Unit) {
      cond.acceptCond(visitor)
    }
  }
  case class Project(rel: RelId, exprs: List[Expr]) extends Op {
    override def acceptCond(expr: Cond => Unit) = ()
  }

  sealed abstract class Cond {
    def acceptCond(visitor: Cond => Unit) {
      visitor(this)
      acceptCondInternal(visitor)
    }
    def acceptCondInternal(visitor: Cond => Unit)
  }
  // case object True extends Cond
  // case object False extends Cond
  case class Conjunction(lhs: Cond, rhs: Cond) extends Cond {
    override def acceptCondInternal(visitor: Cond => Unit) {
      lhs.acceptCond(visitor)
      rhs.acceptCond(visitor)
    }
  }
  case class Negation(child: Cond) extends Cond {
    override def acceptCondInternal(visitor: Cond => Unit): Unit = child.acceptCond(visitor)
  }
  case class Constraint(lhs: Expr, op: CstraintOp, rhs: Expr) extends Cond {
    override def acceptCondInternal(visitor: Cond => Unit): Unit = ()
  }
  case class DoesExist(exprs: List[Expr], rel: RelId) extends Cond {
    override def acceptCondInternal(visitor: Cond => Unit): Unit = ()
  }
  case class IsEmpty(rel: RelId) extends Cond {
    override def acceptCondInternal(visitor: Cond => Unit): Unit = ()
  }

  sealed abstract class Expr
  case class TupleElement(id: Id, elem: Int) extends Expr
  case class ConstValue(value: Any) extends Expr
  case class BinaryExpr(lhs: Expr, op: ExprOp, rhs: Expr) extends Expr
  case object Bot extends Expr

  type CstraintOp = CstraintOp.Value
  object CstraintOp extends Enumeration {
    val EQ, NE, LT, LE, GE, GT = Value
  }
  type ExprOp = ExprOp.Value
  object ExprOp extends Enumeration {
    val ADD, SUB, MUL, DIV = Value
  }

  type AggOp = AggOp.Value
  object AggOp extends Enumeration {
    val Max, Min, Count, Sum = Value
  }

}
