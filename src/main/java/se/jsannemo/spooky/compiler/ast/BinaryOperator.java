package se.jsannemo.spooky.compiler.ast;

public enum BinaryOperator {
  /** Assign value of the right expression to the left reference. */
  ASSIGN,
  /** Add the value of the right expression to the left reference. */
  ASSIGN_ADD,
  /** Check if left value is smaller than right value. */
  LESS_THAN,
  /** Check if left value is greater than right value. */
  GREATER_THAN,
  /** Check if left value is smaller than or equal to the right value. */
  LESS_EQUALS,
  /** Check if left value is greater than or equal to the right value. */
  GREATER_EQUALS,
  /** Check if left value is equal to right value. */
  EQUALS,
  /** Check if left value is not equal to right value. */
  NOT_EQUALS,
  /** Access array at given index. */
  ARRAY_ACCESS,
  /** Add the left and right values.. */
  ADD,
  /** Subtract the right from the left value. */
  SUBTRACT,
  /** Multiply the left and right values */
  MULTIPLY,
  /** Divide the left value by the right value. */
  DIVIDE,
  /** Compute the left value modulo the right value. */
  MODULO,
  /** Compute the logical AND of the two values. */
  AND,
  /** Compute the logical OR of the two values. */
  OR,
}
