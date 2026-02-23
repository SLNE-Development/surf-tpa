plugins {
    id("dev.slne.surf.surfapi.gradle.paper-plugin") version "1.21.11+"
}

group = "dev.slne.surf.tpa"
version = findProperty("version") as String

surfPaperPluginApi {
    mainClass("dev.slne.surf.tpa.PaperMain")
    generateLibraryLoader(false)
    foliaSupported(true)

    authors.add("Jo_field")
}