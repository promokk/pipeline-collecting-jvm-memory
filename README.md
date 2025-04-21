# Pipeline-jmeter-dump-heap
Jenkins pipeline для сбора диагностических данных памяти процессов Jmeter.  
Pipeline подходит для любых java-процессов (JVM).  
На выбранном сервере выполняются диагностические команды с помощью утилиты 
[jcmd](https://www.baeldung.com/running-jvm-diagnose), после данные отправляются на сервер для анализа.  
Возможности: снимок кучи, снимок потоков, снимок классов, выполнить диагностику утечки памяти.

---
# Оглавление
Текст

---
## Начало работы <a id="getStart"></a>
1. **Environment в pipeline.**  
   Обязательные к редактированию:
2. Обязательные properties, которые нужно отредактировать под ваш проект
   * текст

---
## Описание pipeline <a id="pipelineDescription"></a>

---
### Этапы сборки <a id="assemblySteps"></a>
Pipeline состоит из этапов (stage):
* Deleting Workspace  
  Содержит несколько шагов:
    * DELETING WORKSPACE  
      Рекурсивное удаление текущего каталога и его содержимого.
    * CHECK SSH-KEYGEN  
      Если во время сборки возникает ошибка "host key verification failed", то данный шаг решит эту проблему.
* Info  
  Вывод информации по процессам.
* Create File  
  Создание файлов для диагностики памяти.
* Send File  
  Содержит несколько шагов:
    * DOWNLOAD FILE  
      Загрузка архива на сервер Jenkins.
    * SEND FILE  
      Отправка архива на удаленный сервер.
* finally  
  Удаление временной директории.

![assemblySteps - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-dump-heap/main/data/assemblySteps.png)

---
### Результат успешной сборки <a id="resultSuccessfulBuild"></a>
Результат успешной сборки - архив с файлами.  
Архив содержит:
* Файл с информацие по процессам, info\_{SERVER}\_{timeNow}.txt.  
  Данная информация небоходима для идентификации процессов.
  ~~~
  // Пример содержимого файла info_{SERVER}_{timeNow}.txt
  // {pid}: {sun.java.command}
  Process:
	         1649: /opt/jmeter/bin/ApacheJMeter.jar -Dserver_port\=1099 -s -j jmeter-server.log
	         8899: /opt/jmeter/bin/ApacheJMeter.jar -R localhost -n -t scriptExample.jmx
  ~~~
* Снимок кучи, heapDump\_{SERVER}\_{pid}\_{timeNow}.  
  Файл будет создат при выполнении условия HEAPDUMP = true.
* Снимок потоков, threadPrint\_{SERVER}\_{pid}\_{timeNow}.  
  Файл будет создат при выполнении условия THREADPRINT = true.
* Снимок классов, classHistogram\_{SERVER}\_{pid}\_{timeNow}.
  Файл будет создат при выполнении условия CLASSHISTOGRAM = true.  
* Снимок утилизации памяти, diagnoseMemory\_{SERVER}\_{pid}\_{timeNow}.  
  Файл будет создат при выполнении условия DIAGNOSEMEMORY = true.  
* Снимок утилизации памяти с разницей в утилизации памяти с течением времени, 
  diagnoseMemoryLeak_${DIAGNOSETIME}\_{SERVER}\_{pid}\_{timeNow}.    
  Файл будет создат при выполнении условия DIAGNOSEMEMORY = true и заполнен параметр DIAGNOSETIME.  
  Данный файл создается вместо diagnoseMemory.

!!! Мб добавить картинку пример вывода архива в виде дерева (если так можно) !!!  
![XXX - картинка]()

---
### Используемые плагины Jenkins <a id="pluginsUseJenkins"></a>
* Pipeline: Stage View Plugin
* Rebuilder
* SSH Agent Plugin
* SSH Build Agents

---
## Параметры запуска <a id="launchOptions"></a>

---
### SERVER <a id="SERVER"></a>
Сервер с котрого необходимо выболнить сбор диагностических данных памяти.  
Максимальное кол-во серверов при запуске - 1.

![SERVER - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-dump-heap/main/data/SERVER.png)

---
### HEAPDUMP <a id="HEAPDUMP"></a>
Boolen значение, которое определяет, нужно ли выполнит снимок кучи (GC.heap_dump).
* True - выполнить снимок кучи.
* False - пропустить данный этап.

![HEAPDUMP - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-dump-heap/main/data/HEAPDUMP.png)

---
### THREADPRINT <a id="THREADPRINT"></a>
Boolen значение, которое определяет, нужно ли выполнит снимок потоков (Thread.print).
* True - выполнить снимок потоков.
* False - пропустить данный этап.

![THREADPRINT - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-dump-heap/main/data/THREADPRINT.png)

---
### CLASSHISTOGRAM <a id="CLASSHISTOGRAM"></a>
Boolen значение, которое определяет, нужно ли выполнит снимок классов (GC.class_histogram).
* True - выполнить снимок классов.
* False - пропустить данный этап.

![CLASSHISTOGRAM - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-dump-heap/main/data/CLASSHISTOGRAM.png)

---
### DIAGNOSEMEMORY <a id="DIAGNOSEMEMORY"></a>
Boolen значение, которое определяет, нужно ли выполнит снимок утилизации памяти или диагностику утечки памяти (VM.native_memory).
* True - выполнить снимок утилизации памяти.  
Дигностика утечки памяти выполняется, если указан параметр DIAGNOSETIME.
* False - пропустить данный этап.

![DIAGNOSEMEMORY - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-dump-heap/main/data/DIAGNOSEMEMORY.png)

---
### DIAGNOSETIME <a id="DIAGNOSETIME"></a>
Продолжительность диагностики утечки памяти. Указывается в секундах.  
Параметр заполняется, если небоходимо выполнить диагностику утечки памяти.  
Параметр можно заполнить, если DIAGNOSEMEMORY = True. В иных случаях параметр будет игнорироваться.
* Параметр заполнен.  
Фиксируется базовый уровень утилизации памяти на момент выполнения команды (VM.native_memory baseline).  
Через {DIAGNOSETIME} секунд выполняется команда для повторного снимка утилизации памяти (VM.native_memory summary.dif). 
Это позволяет определить разницу в утилизации памяти с течением времени. Помогает выявить потенциальную утечку памяти.
* Параметр не заполнен.  
Выполнить снимок утилизации памяти (VM.native_memory).

![DIAGNOSETIME - картинка](https://raw.githubusercontent.com/promokk/pipeline-jmeter-dump-heap/main/data/DIAGNOSETIME.png)

















