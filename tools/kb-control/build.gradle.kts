// Control test of the combat engine: compares what a real 1.8.8 server sent to test bots with
// what rago-combat-model predicts. Runs in CI and on a developer machine, never on the network.
plugins {
    id("rago.java-conventions")
    application
}

dependencies {
    implementation(project(":rago-combat-model"))
    implementation(libs.gson)
}

application {
    mainClass.set("gg.rago.tools.kbcontrol.ControlCheck")
}
