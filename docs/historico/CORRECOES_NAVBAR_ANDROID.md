# Correção da navbar inferior

Correção aplicada para a barra de navegação inferior do app não ficar escondida atrás dos botões do Android.

## Arquivo alterado
- `app/src/main/java/com/example/ecotherms/BaseActivity.kt`

## O que foi feito
- Mantido o modo edge-to-edge.
- Adicionada leitura dos `WindowInsetsCompat`.
- A `bottomNavBar` agora aumenta a altura automaticamente conforme a área dos botões do Android.
- A navbar recebe `paddingBottom` igual ao espaço ocupado pela barra de navegação do sistema.
- Em telas sem `bottomNavBar`, o layout raiz recebe padding inferior seguro.

## Resultado esperado
A barra inferior do Ecotherms fica sempre acima dos botões nativos do Android, tanto em navegação por gestos quanto em navegação por 3 botões.
