grammar System;

// ----------------------------------------------------------------------
// 파서 규칙: 문장의 구조를 정의합니다.
// ----------------------------------------------------------------------
prog: statement+ EOF;

statement
    : create_starrocks_statement
    | drop_starrocks_statement
    | create_job_statement
    | insert_job_statement
    | create_schedule_statement
    | select_statement // select 문 추가
    ;

create_starrocks_statement
    : CREATE STARROCKS ID (size_spec | resource_spec)? SEMI
    ;
size_spec
    : SIZE LPAREN ID RPAREN
    ;
resource_spec
    : FE LPAREN spec_list RPAREN CN LPAREN spec_list RPAREN
    ;
spec_list
    : spec_item (COMMA spec_item)*
    ;
spec_item
    : ID COLON INT
    ;

drop_starrocks_statement
    : DROP STARROCKS ID SEMI          // DROP STARROCKS <이름>;
    ;

create_job_statement
    : CREATE JOB ID SEMI
    ;

insert_job_statement
    : INSERT INTO ID NAME ID TYPE ID FROM ID TO ID QUERY LPAREN query_content RPAREN SEMI
    ;

query_content
    : query_token+
    ;
query_token
    : ID | INT | STRING | STAR | FROM | COLON | COMMA | LPAREN | RPAREN | SEMI
    ;

create_schedule_statement
    : CREATE SCHEDULE ID param_list SEMI
    ;
param_list
    : LPAREN param_item (COMMA param_item)* RPAREN
    ;
param_item
    : ID COLON param_value
    ;
param_value
    : ID | STRING
    ;

select_statement
    : SELECT STAR FROM ID SEMI // select * from ID; 구문 추가
    ;

// ----------------------------------------------------------------------
// 렉서 규칙: 토큰(단어)을 정의합니다.
// ----------------------------------------------------------------------
CREATE: 'CREATE';
DROP: 'DROP';
STARROCKS: 'STARROCKS';
SIZE: 'size';
FE: 'FE';
CN: 'CN';
JOB: 'JOB';
INSERT: 'insert';
INTO: 'into';
NAME: 'name';
TYPE: 'type';
FROM: 'from';
TO: 'to';
QUERY: 'query';
SCHEDULE: 'SCHEDULE';
SELECT: 'select'; // select 렉서 규칙 추가

ID: [a-zA-Z_][a-zA-Z0-9_]*;
INT: [0-9]+;
STRING: '\'' ( ~['\n\r\\] | '\\' . )* '\'' ;
STAR: '*';
SEMI: ';';
COLON: ':';
COMMA: ',';
LPAREN: '(';
RPAREN: ')';

WS: [ \t\r\n]+ -> skip;