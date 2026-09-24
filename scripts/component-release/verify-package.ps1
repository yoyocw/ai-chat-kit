[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Repository,

    [string]$Version = "1.2.0-SNAPSHOT"
)

$ErrorActionPreference = "Stop"
$repositoryPath = [System.IO.Path]::GetFullPath($Repository)
if (-not (Test-Path -LiteralPath $repositoryPath -PathType Container)) {
    throw "Maven repository does not exist: $repositoryPath"
}

$artifacts = @(
    "ai-chat-kit-mcp-jwt",
    "ai-chat-kit-contract",
    "ai-chat-kit-engine",
    "ai-chat-kit-starter",
    "ai-chat-kit-adapter-web",
    "ai-chat-kit-adapter-jdbc",
    "ai-chat-kit-adapter-mcp-jwt-v1",
    "ai-chat-kit-adapter-hosted-proxy"
)

$forbiddenArtifactIds = @(
    "spring-cloud-starter-alibaba-nacos-discovery",
    "spring-cloud-starter-alibaba-nacos-config"
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$results = foreach ($artifactId in $artifacts) {
    $artifactDirectory = Join-Path $repositoryPath "io/github/yoyocw/$artifactId/$Version"
    $binaryJar = Join-Path $artifactDirectory "$artifactId-$Version.jar"
    $sourcesJar = Join-Path $artifactDirectory "$artifactId-$Version-sources.jar"
    $pom = Join-Path $artifactDirectory "$artifactId-$Version.pom"

    foreach ($requiredFile in @($binaryJar, $sourcesJar, $pom)) {
        if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
            throw "Missing candidate artifact: $requiredFile"
        }
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($binaryJar)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        if ($entryNames -contains "BOOT-INF/classes/") {
            throw "$artifactId is a Spring Boot executable archive, not a thin component JAR"
        }
        if ($entryNames | Where-Object { $_ -like "BOOT-INF/*" }) {
            throw "$artifactId contains BOOT-INF entries and is not a thin component JAR"
        }
        if ($entryNames -notcontains "META-INF/LICENSE") {
            throw "$artifactId does not contain META-INF/LICENSE"
        }
    }
    finally {
        $archive.Dispose()
    }

    $pomText = Get-Content -LiteralPath $pom -Raw
    [xml]$publishedPom = $pomText
    if ([string]$publishedPom.project.groupId -ne "io.github.yoyocw" -or
        [string]$publishedPom.project.artifactId -ne $artifactId -or
        [string]$publishedPom.project.version -ne $Version) {
        throw "$artifactId published POM has unexpected coordinates"
    }
    if ($null -ne $publishedPom.project.parent) {
        throw "$artifactId published POM still contains a parent"
    }
    if ($pomText -match '\$\{revision\}') {
        throw "$artifactId published POM still contains an unresolved revision property"
    }
    if ($pomText -match '<relativePath(?:\s|>|/)') {
        throw "$artifactId published POM contains a source-tree relativePath"
    }
    foreach ($dependency in $publishedPom.SelectNodes("/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency']")) {
        $dependencyGroup = $dependency.SelectSingleNode("./*[local-name()='groupId']").InnerText.Trim()
        $dependencyArtifact = $dependency.SelectSingleNode("./*[local-name()='artifactId']").InnerText.Trim()
        if ($dependencyGroup -eq 'io.github.yoyocw' -and $dependencyArtifact -notin $artifacts) {
            throw "$artifactId published POM references a non-neutral project artifact $dependencyArtifact"
        }
    }
    foreach ($artifactIdNode in $publishedPom.SelectNodes("//*[local-name()='artifactId']")) {
        $referencedArtifactId = $artifactIdNode.InnerText.Trim()
        if ($referencedArtifactId -match '^(platform-compat-|ai-chat-kit-(legacy-|host-))' -or
            $forbiddenArtifactIds -contains $referencedArtifactId) {
            throw "$artifactId published POM leaks forbidden platform artifact $referencedArtifactId"
        }
    }
    if ([string]$publishedPom.project.url -ne "https://github.com/yoyocw/ai-chat-kit") {
        throw "$artifactId published POM has an unexpected project URL: $($publishedPom.project.url)"
    }
    if ([string]$publishedPom.project.scm.url -ne "https://github.com/yoyocw/ai-chat-kit") {
        throw "$artifactId published POM has an unexpected SCM URL: $($publishedPom.project.scm.url)"
    }
    foreach ($scmConnection in @($publishedPom.project.scm.connection, $publishedPom.project.scm.developerConnection)) {
        if ([string]$scmConnection -ne "scm:git:https://github.com/yoyocw/ai-chat-kit.git") {
            throw "$artifactId published POM has an unexpected SCM connection: $scmConnection"
        }
    }
    if ($pomText -match 'https://repo\.osgeo\.org/') {
        throw "$artifactId neutral published POM leaks the Platform-only OSGeo repository"
    }

    [pscustomobject]@{
        Artifact = $artifactId
        Version = $Version
        JarSha256 = (Get-FileHash -LiteralPath $binaryJar -Algorithm SHA256).Hash
        SourcesSha256 = (Get-FileHash -LiteralPath $sourcesJar -Algorithm SHA256).Hash
        PomSha256 = (Get-FileHash -LiteralPath $pom -Algorithm SHA256).Hash
    }
}

$results
Write-Output "Verified $($results.Count) neutral component artifacts in $repositoryPath"
