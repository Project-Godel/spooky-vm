arrayIndex
    : refOrCall
    | arrayIndex op=LBRACKET index=expr RBRACKET
    ;

refOrCall
    : id=IDENTIFIER (LPAREN (args=exprList)? RPAREN)?
    | LPAREN e=expr RPAREN
    | literal
    ;

literal
    : sign=MINUS? tok=NUM_INT   # IntLit
    | tok=STRING                # StringLit
    | tok=TRUE                  # BoolTrue
    | tok=FALSE                 # BoolFalse
    ;

exprList
    : e+=expr (COMMA e+=expr)*
    ;

