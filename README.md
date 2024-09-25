# Compilação do projeto e rodar

## Rode no Modo GUI

```
make release.gui

```

## Rode no modo Terminal

```
make release

```

## Rode como abaixo, caso tenha apenas acesso ao `.jar`.


```
java -jar <arquivo_jar>

```

### Windows
No sistema operacional Windows, é possível clicar com o botão direito do mouse no arquivo `.jar` e selecionar a opção "Abrir com Java Runtime".

![Java Runtime](./img/sd-windows-open_with_java_runtime.png)

Observação: Devido à falta de suporte a `ANSI Escape Codes`, e dificuldade para usar ``cooked mode`` no terminal Windows, o sistema detecta automaticamente o sistema operacional e executa apenas no modo GUI.

Esses `ANSI Escape Codes` são necessários para manter uma experiencia legal ao digitar no terminal e não ter suas mensagens ocludidas ao receber novas mensagens. Em sistemas Unix pode usar o modo terminal tranquilamente.


## Projeto

- Modo Gui

![Gui Mode](./img/sd-gui.png)

- Modo Terminal

![Terminal Mode](./img/sd-terminal.png)

- Envio de Arquivos por partes

![Seding Files](./img/sd-files.png)


Veja ``etapas.md`` para ver a capacidade basica do projeto.

Além dessas capacidades, temos supporte a commandos extras como "!whoami". Use  "!help" para ver todos os comandos. Ademais, temos:

- Auto-completar: O sistema oferece suporte ao auto-completar para caminhos de arquivos, comandos, usuários e grupos do RabbitMQ. Basta pressionar a tecla TAB para acessar essas sugestões.

- Edição: A linha onde se escreve suporta edição mais ergonomica, como deletar uma palavra com crtl+w, ou pular palavra com crtl+seta.

- Historico de texto: O sistema lembra dos ultimos texto ou comandos enviados pelo usuário. Com a seta para cima ou para baixo podemos selecionar esses comandos por ordem de envio.

- Modo Terminal: O prompt permanece visível durante toda a execução, mesmo quando novas mensagens estão sendo recebidas, garantindo uma melhor experiência de usuário.

- Envio de Arquivos: O sistema envia um reconhecimento (Ack) ao RabbitMQ somente após a confirmação de que o arquivo foi gravado em disco. Além disso, os arquivos podem ser enviados em partes, evitando sobrecarga no servidor.






## Configurações Testadas
As seguintes versões de software foram utilizadas para testar o projeto. Mesmo assim foi feito um esforço
para utilizar o menor possivel de features atuais e a compilação foi configurada para usar a menor versão do java runtime possivel.

    $ mvn --version
    Apache Maven 3.9.8 (36645f6c9b5079805ea5009217e36f2cffd34256)

    $ java --version
    java 22.0.2 2024-07-16
    Java(TM) SE Runtime Environment (build 22.0.2+9-70)
    Java HotSpot(TM) 64-Bit Server VM (build 22.0.2+9-70, mixed mode, sharing)

    $ protoc --version
    libprotoc 27.3


## Configuração do Protocol Buffers


- Instalação do compilador protoc

```
sudo apt install protobuf-compiler
```

-  Uso do compilador protoc

```
protoc --java_out=src/main/java/ src/main/proto/message.proto

```

## Configuração do RabbitMQ

Para mais informações sobre a configuração do RabbitMQ, consulte o arquivo ``rabbitmq.md``
