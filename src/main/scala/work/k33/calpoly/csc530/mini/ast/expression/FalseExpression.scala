package work.k33.calpoly.csc530.mini.ast.expression

import work.k33.calpoly.csc530.BoolS
import work.k33.calpoly.csc530.mini.ast.MiniAST
import work.k33.calpoly.csc530.mini.ast.types.{BoolType, Tables, Type}
import work.k33.calpoly.csc530.mini.interp.{Bool, MiniValues, State}

case class FalseExpression(lineNum: Int) extends Expression {
  def typeCheck(tables: Tables): Type = {
    BoolType()
  }

  def interp(state: State): MiniValues = MiniValues(Bool(false), BoolS(false))

  def gatherTerms: Set[MiniAST] = Set(this)
}
