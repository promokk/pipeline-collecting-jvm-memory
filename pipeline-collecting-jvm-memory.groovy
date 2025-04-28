node {
    // environment
    // Пользователь для подключения по ssh к SERVER
    def userSSH = 'user'
    // Сredential для подключения по ssh
    def credentialSSH = 'credential-ssh'
    // Директория в которой будет создана временная директория для файлов (workDir)
    def homeDir = '/dir'
    // Временная директория в которой будут создаваться файлы
    def workDir = 'dirHeapDumps'
    // Пользователь для подключения по ssh к удаленному серверу с архивами
    def remoteUserSSH = 'user'
    // Удаленный сервер на который будет отправлен архив с файлами
    def remoteServer = '192.168.1.10'
    // Директория на сервере {remoteServer} в которую будет отправлен архив с файлами
    def remoteServerDirPath = '/dir'
    // Наименование искомого процесса
    def processName = 'processName.jar'
    // Уровень отслеживания памяти (summary / detail)
    def memoryTrackingLevel = 'detail'
    // Путь до workspace на сервере Jenkins
    def workspacePath = env.WORKSPACE


    try {
        properties([
            disableConcurrentBuilds(),
            buildDiscarder(
                logRotator(
                    artifactDaysToKeepStr: '',
                    artifactNumToKeepStr: '',
                    daysToKeepStr: '14',
                    numToKeepStr: '30')
            ),
            parameters([
                string(
                        name: 'SERVER',
                        defaultValue: '192.168.1.111',
                        description: 'Сервер',
                        trim: true),
                booleanParam(
                        name: 'HEAPDUMP',
                        defaultValue: true,
                        description: 'Снимок кучи (GC.heap_dump)'),
                booleanParam(
                        name: 'THREADPRINT',
                        defaultValue: false,
                        description: 'Снимок потоков (Thread.print)'),
                booleanParam(
                        name: 'CLASSHISTOGRAM',
                        defaultValue: false,
                        description: 'Снимок классов (GC.class_histogram)'),
                booleanParam(
                        name: 'DIAGNOSEMEMORY',
                        defaultValue: false,
                        description: 'Снимок утилизации памяти (VM.native_memory)'),
                string(
                        name: 'DIAGNOSETIME',
                        description: '''Продолжительность диагностики утечки памяти, сек.
Параметр заполняется, если небоходимо выполнить диагности утечки памяти.
Параметр можно заполнить, если DIAGNOSEMEMORY = True. В иных случаях параметр будет игнорироваться.''',
                        trim: true)
            ])])

        // environment
        // Время 'сейчас' в миллисекундах
        timeNow = System.currentTimeMillis()
        // Массив процессов
        try {
            pidArr = sh(returnStdout: true, script: "ssh ${userSSH}@${SERVER} 'pgrep -f ${processName}'").trim().split('\n')
        } catch(Exception e) {
            error "Failed, process not found."
        }

        stage('Deleting Workspace') {
            // Очистка WORKSPACE
            echo '-----------------DELETING WORKSPACE-----------------'
            deleteDir()

            // Удаление и добавление ssh-ключа
			echo '-----------------CHECK SSH-KEYGEN-----------------'
            sh "ssh-keygen -R ${SERVER}"
            sh "ssh-keyscan ${SERVER} >> ~/.ssh/known_hosts"
        }
        stage('Info') {
            // Вывод информации по процессам
            echo '-----------------INFO-----------------'
            def messageInfo = "Process:"
            for (pid in pidArr) {
                def infoPid = sh(returnStdout: true, script: "ssh ${userSSH}@${SERVER} 'jcmd ${pid} VM.system_properties | grep sun.java.command'").trim().replace('sun.java.command=', "${pid}: ")
                messageInfo += "\n\t${infoPid}"
            }
            echo messageInfo

            currentBuild.displayName = "#${env.BUILD_ID}--${SERVER}"
            currentBuild.description = "${messageInfo}"

            // Создание файла с информацией по процессам
            sh """ssh ${userSSH}@${SERVER} '''rm -r ${homeDir}/${workDir}; mkdir ${homeDir}/${workDir}
cat << EOF > ${homeDir}/${workDir}/info_${SERVER}_${timeNow}.txt
${messageInfo}
EOF
'''"""
        }
        stage('Create File') {
            sshagent(["${credentialSSH}"]) {
                // Создание файлов для диагностики памяти
                echo '-----------------CREATE FILE-----------------'
                for (pid in pidArr) {
                    if (params.DIAGNOSEMEMORY) {
                        if (params.DIAGNOSETIME) {
                            echo 'Create Baseline Diagnose Memory Leak...'
                            sh "ssh ${userSSH}@${SERVER} 'jcmd ${pid} VM.native_memory baseline'"
                        } else {
                            echo 'Create Diagnose Memory...'
                            sh "ssh ${userSSH}@${SERVER} 'jcmd ${pid} VM.native_memory > ${homeDir}/${workDir}/diagnoseMemory_${SERVER}_${pid}_${timeNow}'"
                        }
                        if (params.DIAGNOSETIME) {
                            echo "Continued in ${DIAGNOSETIME} seconds..."
                            sleep params.DIAGNOSETIME
                            echo 'Create Difference To Diagnose Memory Leak...'
                            sh "ssh ${userSSH}@${SERVER} 'jcmd ${pid} VM.native_memory ${memoryTrackingLevel}.diff > ${homeDir}/${workDir}/diagnoseMemoryLeak_${DIAGNOSETIME}_${SERVER}_${pid}_${timeNow}'"
                        }
                    }
                    if (params.HEAPDUMP) {
                        echo 'Create Heap Dump...'
                        sh "ssh ${userSSH}@${SERVER} 'jcmd ${pid} GC.heap_dump ${homeDir}/${workDir}/heapDump_${SERVER}_${pid}_${timeNow}'"
                    }
                    if (params.THREADPRINT) {
                        echo 'Create Thread Print...'
                        sh "ssh ${userSSH}@${SERVER} 'jcmd ${pid} Thread.print > ${homeDir}/${workDir}/threadPrint_${SERVER}_${pid}_${timeNow}'"
                    }
                    if (params.CLASSHISTOGRAM) {
                        echo 'Create Class Histogram...'
                        sh "ssh ${userSSH}@${SERVER} 'jcmd ${pid} GC.class_histogram > ${homeDir}/${workDir}/classHistogram_${SERVER}_${pid}_${timeNow}'"
                    }
                }
                fileStr = sh(returnStdout: true, script: "ssh ${userSSH}@${SERVER} 'ls ${homeDir}/${workDir}'").trim().replaceAll('\\s', ' ')
                sh "ssh ${userSSH}@${SERVER} 'cd ${homeDir}/${workDir}; zip memoryUsage_${SERVER}_${timeNow}.zip ${fileStr}'"
            }
        }
        stage('Send File') {
            sshagent(["${credentialSSH}"]) {
                // Загрузка архива на сервер Jenkins
                echo '-----------------DOWNLOAD FILE-----------------'
                archiveFile = sh(returnStdout: true, script: "ssh ${userSSH}@${SERVER} 'ls ${homeDir}/${workDir} | grep memoryUsage'").trim()
                sh "scp ${userSSH}@${SERVER}:${homeDir}/${workDir}/${archiveFile} ${workspacePath}"

                // Отправка архива на удаленный сервер
                echo '-----------------SEND FILE-----------------'
                sh "scp ${archiveFile} ${remoteUserSSH}@${remoteServer}:${remoteServerDirPath}"
            }
        }
    }  catch (e) {
        echo "Failed because of {$e}"
    } finally {
        sshagent(["${credentialSSH}"]) {
            // Удаление временной директории
            echo '-----------------END-----------------'
            sh "ssh ${userSSH}@${SERVER} 'rm -r ${homeDir}/${workDir}'"
        }
    }
}
