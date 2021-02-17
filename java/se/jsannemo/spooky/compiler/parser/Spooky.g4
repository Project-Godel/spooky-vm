grammar Spooky;

program
    : topLevel* EOF
    ;

topLevel
    : globalVariable
    | extern
    | function
    ;

globalVariable
    : varDecl ';'
    ;

extern
    : EXTERN id=IDENTIFIER LPAREN params=paramList? RPAREN
    ;

function
    : decl=funcDecl body=block;

funcDecl
    : FUNC id=IDENTIFIER LPAREN params=paramList? RPAREN
    ;

paramList
    : param+=varDecl (COMMA param+=varDecl)*
    ;

block
    : LBRACE statementList? RBRACE
    ;

statementList
    : s+=statement (s+=statement)*
    ;

statement
    : conditional
    | loop
    | block
    | varDecl SEMICOLON
    | expr SEMICOLON
    ;

simpleStatement
    : varDecl
    | expr
    ;

conditional
    : IF LPAREN cond=expr RPAREN body=statement (ELSE elseBody=statement)?
    ;

loop
    : forLoop
    | whileLoop
    ;

forLoop
    : FOR LPAREN (init=simpleStatement)? SEMICOLON (cond=expr)? SEMICOLON (increment=simpleStatement)? ')' statement
    ;

whileLoop
    : WHILE LPAREN (cond=expr) RPAREN statement
    ;

varDecl
    : var=IDENTIFIER COLON type=typeName
    ;

typeName
    : name=IDENTIFIER (arr += LBRACKET RBRACKET) *
    ;

expr
    : ternaryExpr
    ;

ternaryExpr
    : e=orExpr (QUESTION e1=orExpr COLON e2=ternaryExpr)?
    ;

orExpr
    : e=andExpr (ops+=OR e1+=andExpr)*
    ;

andExpr
    : e=cmpExpr (ops+=AND e1+=cmpExpr)*
    ;

cmpExpr
    : arithExpr
    | cmpExpr op=(LESS | LESS_EQUALS | GREATER_EQUALS | GREATER | EQUALS | NOT_EQUALS) cmpExpr
    ;

arithExpr
    : unary
    | arithExpr op=(ASTERISK | SLASH | PERCENT) arithExpr
    | arithExpr op=(PLUS | MINUS) arithExpr
    ;

unary
    : arrayIndex
    | (ops+=EXCLAIM)+ arrayIndex
    | (ops+=MINUS)+ arrayIndex
    ;

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

// Lexer Rules
// ===========

// Tokens to ignore
WHITESPACE : ( '\t' | ' ' | '\r' | '\n'| '\u000C' )+ -> channel(HIDDEN) ;
SINGLE_LINE_COMMENT : '//' (~'\n')* -> channel(HIDDEN) ;

// Character classes
fragment BACKSLASH : '\\';
fragment ALPHA : 'A'..'Z' | 'a'..'z' ;
fragment DIGIT  : '0'..'9' ;
fragment ESCAPE_CHAR : BACKSLASH ('a' | 'b' | 'f' | 'n' | 'r' | 't' | 'v' | '"' | '\\');

// Keywords
ELSE: 'else';
EXTERN : 'extern';
FOR: 'for';
FUNC : 'func';
IF : 'if';
WHILE: 'while';
TRUE : 'true';
FALSE : 'false';

// Operators
EQUALS : '==';
NOT_EQUALS : '!=';
LESS : '<';
LESS_EQUALS : '<=';
GREATER_EQUALS : '>=';
GREATER : '>';
AND : '&&';
OR : '||';
DOT : '.';
COMMA : ',';
MINUS : '-';
EXCLAIM : '!';
QUESTION : '?';
PLUS : '+';
ASTERISK : '*';
SLASH : '/';
PERCENT : '%';

LBRACKET : '[';
RBRACKET : ']';
LBRACE : '{';
RBRACE : '}';
LPAREN : '(';
RPAREN : ')';
COLON : ':';
SEMICOLON : ';';

IDENTIFIER : (ALPHA | '_') (ALPHA | DIGIT | '_')*;

// Literals
NUM_INT : DIGIT+;
STRING : '"' (ESCAPE_CHAR | ~('\\' | '"' | '\n' | '\r'))* '"';
