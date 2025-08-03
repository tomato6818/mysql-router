grammar Context;

file: statement EOF;

statement: context_declaration_statement | query_statement;

context_declaration_statement: CONTEXT_TOKEN SEMI;

query_statement: (TEXT_TOKEN | SEMI)+;

CONTEXT_TOKEN: '%starrocks' | '%pyspark' | '%system';
SEMI: ';';
TEXT_TOKEN: . ;
WS: [ \t\r\n]+ -> skip;
