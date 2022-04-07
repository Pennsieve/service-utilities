#!groovy

node("executor") {
    checkout scm

    def commitHash  = sh(returnStdout: true, script: 'git rev-parse HEAD | cut -c-7').trim()
    def imageTag = "${env.BUILD_NUMBER}-${commitHash}"

    def sbt = "sbt -Dsbt.log.noformat=true -Dversion=$imageTag"

    def pennsieveNexusCreds = usernamePassword(
        credentialsId: "pennsieve-nexus-ci-login",
        usernameVariable: "PENNSIEVE_NEXUS_USER",
        passwordVariable: "PENNSIEVE_NEXUS_PW"
    )

    stage("Build") {
        withCredentials([pennsieveNexusCreds]) {
            sh "$sbt clean +compile"
        }
    }

    stage("Test") {
        withCredentials([pennsieveNexusCreds]) {
            sh "$sbt +test"
        }
    }

    if (env.BRANCH_NAME == "main") {
        stage("Publish") {
            withCredentials([pennsieveNexusCreds]) {
                sh "$sbt +publish"
            }
        }
    }
}
