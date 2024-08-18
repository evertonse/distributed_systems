### ProtocolBufferersExample

- Instalação do compilador protoc

```
sudo apt install protobuf-compiler
```

-  Uso do compilador protoc

```
protoc --java_out=src/main/java/ src/main/proto/aluno.proto
```

## Compilação do projeto e rodar

```
make run

```

# ChatRabbitMQ

Este projeto consiste no desenvolvimento de um cliente de chat do tipo linha de comando usando o RabbitMQ como servidor de mensagens de acordo com o apresentado em sala de aula.

Este projeto será desenvolvido composto por etapas, conforme o andamento das aulas da disciplina. Cada etapa será descrita em um arquivo ".md" específico neste mesmo repositório.


Esse projeto deve ser feito a partir do seguinte repositório base: https://classroom.github.com/a/7G_WRqkb

Esse projeto usará uma instância do RabbitMQ instalada no serviço EC2 da AWS. Os passos para essa instalação se encontra no arquivo "rabbitmw-ec2.md" neste mesmo repositório.



------------------------------------------------------------------------------------------
# Etapa 1

## Implementação da Interface do Chat em Linha de Comando

Ao ser executado, o chat perguntaria o nome do usuário do mesmo. Exemplo:
```
User:
```

Com isso, o usuário digita o seu nome de usuário. Exemplo:
```
User: tarcisiorocha
```

Com o nome do usuário, o chat cria a fila do usuário no RabbitMQ e exibe um prompt para que o usuário inicie a comunicação. Exemplo de prompt:
```
>>
```
### Envio de mensagens

No prompt, se o usuário (tarcisiorocha) quer enviar mensagem para um outro usuário do chat, ele deve digitar "@" seguido do nome do usuário com o qual ele quer conversar. Exemplo:
```
>> @joaosantos
```

Com isso, o prompt é alterado automaticamente para exibir o nome do outro usuário para o qual se quer enviar mensagem. Exemplo:
```
@joaosantos>>
```

Nesse exemplo, toda nova mensagem digitada no prompt é enviada para "joaosantos" até que o usuário mude para para um novo destinatário. Exemplo:
```
@joaosantos>> Olá, João!!!
@joaosantos>> Vamos adiantar o trabalho de SD?
```
Por exemplo, se o usuário quiser enviar mensagens para outro usuário diferente de "joaosantos", ele deve informar o nome do outro usuário para o qual ele quer enviar mensagem:
```
@joaosantos>> @marciocosta
```
O comando acima faria o prompt ser "chaveado" para "marciocosta". Com isso, as próximas mensagens seriam enviadas para o usuário "marciocosta":
```
@marciocosta>> Oi, Marcio!!
@marciocosta>> Vamos sair hoje?
@marciocosta>> Já estou em casa!
@marciocosta>>
```

### Recebimento de Mensagens

A qualquer momento, o usuário (exemplo: tarcisiorocha) pode receber mensagem de qualquer outro usuário (marciocosta, joaosantos...). Nesse caso, a mensagem seria impressa na tela da seguinte forma:
```
(21/09/2016 às 20:53) marciocosta diz: E aí, Tarcisio! Vamos sim!
```
Depois de impressa a mensagem, o prompt volta para o estado anterior:

```
@marciocosta>>
```
Agora segue um exemplo de três mensagens recebidas de joaosantos:
```
(21/09/2016 às 20:55) joaosantos diz: Opa!
@marciocosta>> 

(21/09/2016 às 20:55) joaosantos diz: vamos!!!
@marciocosta>> 
(21/09/2016 às 20:56) joaosantos diz: estou indo para a sua casa
@marciocosta>> 
```

------------------------------------------------------------------------------------------
# Etapa 2
