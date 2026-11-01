# Correções realizadas no Ecotherms

## Layout responsivo
- Criado `res/values/dimens.xml` e `res/values-sw600dp/dimens.xml` para adaptar paddings, logos, altura do gráfico, foto da planta, botões e barra inferior em telas pequenas e tablets.
- Substituídas dimensões fixas principais por `@dimen/...` nas telas Home, Dados/Histórico, Scanner e Resultado.
- Aplicado `fitsSystemWindows` nas telas principais para evitar corte por status bar/navigation bar.
- Mantida rolagem nas telas longas para evitar quebra em celulares menores.

## Crash do histórico/dados
- Corrigida leitura de números do Firebase: agora aceita `Long`, `Double`, `Float`, `String` e `Boolean`, evitando crash de conversão em dados históricos.
- O gráfico agora limpa e mostra estado seguro quando ainda não existem dados no histórico.
- O gráfico usa linha simples quando há apenas 1 ponto, evitando instabilidade no modo curvo.
- O botão atualizar só tenta recarregar quando a referência e o listener do Firebase já existem.

## Histórico de plantas
- Corrigido cadastro para salvar automaticamente o usuário logado.
- Sanitização da chave do Firebase para e-mails, evitando caracteres inválidos em paths.
- O salvamento no histórico não quebra caso o usuário não seja encontrado; exibe mensagem amigável.

## Estabilidade geral
- `unregisterReceiver` protegido com `runCatching`, evitando crash ao pausar telas.
- Removida gravação de teste `message = Hello, World!` do Firebase na abertura da Home.
- Adicionado plugin Kotlin Android ao Gradle do app.

Observação: não consegui compilar dentro deste ambiente porque o Gradle Wrapper tentou baixar a distribuição da internet e a rede do sandbox está bloqueada. O projeto foi corrigido diretamente nos arquivos-fonte e XML.
