package sdl

// Stmt
//     Load
//     Store
//     Clear
//     Swap
//     Query
//     Seq
//     Loop
//     Exit

// 
package object Ast {

  type Id = String
  type RelId = String
  type Field = String

  sealed abstract class Op
  sealed abstract class NestedOp extends Op {
    val child: Op
  }
  sealed trait IndexedOp extends Op {
    val indexedField: Field
    val expr: Expr
  }
  sealed abstract class AbstractScan extends NestedOp {
    val v: Id
    val rel: RelId
  }
  case class IndexScan(v: Id, rel: RelId, indexedField: Field, expr: Expr, child: Op) extends AbstractScan with IndexedOp
  case class Scan(v: Id, rel: RelId, child: Op) extends AbstractScan

  sealed abstract class AbstractChoice extends NestedOp {
    val v: Id
    val rel: RelId
    val cond: Cond
  }
  case class IndexChoice(v: Id, rel: RelId, cond: Cond, indexedField: Field, expr: Expr, child: Op) extends AbstractChoice with IndexedOp
  case class Choice(v: Id, rel: RelId, cond: Cond, child:Op) extends AbstractChoice

  case class Filter(cond: Cond, child:Op) extends NestedOp
  case class Project(rel: RelId, exprs: List[Expr]) extends Op

  sealed abstract class Cond
  case object True extends Cond
  case object False extends Cond
  case class Conjunction(lhs: Cond, rhs: Cond) extends Cond
  case class Negation(child: Cond) extends Cond
  case class Constraint(lhs: Expr, op: CstraintOp, rhs: Expr) extends Cond
  case class DoesExist(exprs: List[Expr], rel: RelId) extends Cond
  case class IsEmpty(rel: RelId) extends Cond

  sealed abstract class Expr
  case class TupleElement(rel: RelId, elem: Int) extends Expr
  case class Const(value: Element) extends Expr
  case class BinaryExpr(lhs: Expr, op: ExprOp, rhs: Expr) extends Expr


  type CstraintOp = CstraintOp.Value
  object CstraintOp extends Enumeration {
    val EQ, NE, LT, LE, GE, GT = Value
  }
  type ExprOp = ExprOp.Value
  object ExprOp extends Enumeration {
    val ADD, SUB, MUL, DIV = Value 
  }
  sealed abstract class Element
  case class IntElement(value: Int) extends Element
  case class StringElement(value: String) extends Element

  type AggOp = AggOp.Value
  object AggOp extends Enumeration {
    val Max, Min, Count, Sum = Value
  }
}