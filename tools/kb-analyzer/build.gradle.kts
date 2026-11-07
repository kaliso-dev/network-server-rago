// Analysis of kb-recorder recordings: compares the knockback and the hit timing of fights
// recorded on two servers. Runs on a developer machine, never on the network.
plugins {
    id("rago.java-conventions")
    application
}

dependencies {
    implementation(project(":rago-combat-model"))
    implementation(libs.gson)
}

application {
    mainClass.set("gg.rago.tools.kbanalyzer.KbAnalyzer")
}
