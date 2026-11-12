# Serves the Solace quest editor without the game. Same API as EditorServer.java, backed by the
# snapshot the game wrote on its last world load (this folder) and the live files in ../quests.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Web.Extensions
$json = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$json.MaxJsonLength = [int]::MaxValue
$json.RecursionLimit = 512

$here = $PSScriptRoot
$root = Join-Path (Split-Path $here -Parent) 'quests'
$kinds = 'chapters', 'quests', 'dialogues', 'npcs', 'factions', 'markers'
$utf8 = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path (Join-Path $here 'index.html'))) {
    Write-Host 'No editor snapshot yet. Launch the game and load a world once, then try again.'
    Read-Host 'Press Enter to close'
    exit 1
}

function Read-Json($file) { $json.DeserializeObject([IO.File]::ReadAllText($file)) }

function Get-Content-Json {
    $entries = [ordered]@{}
    $problems = @()
    $snapshot = Join-Path $here 'datapack.json'
    if (Test-Path $snapshot) {
        foreach ($item in (Read-Json $snapshot)) {
            $entries[$item['kind'] + '/' + $item['id']] = @{ kind = $item['kind']; id = $item['id']; source = 'datapack'; json = $item['json'] }
        }
    }
    if (Test-Path $root) {
        foreach ($file in Get-ChildItem $root -Recurse -File -Filter *.json) {
            # <namespace>/<kind>/<path...>.json
            $parts = $file.FullName.Substring($root.Length + 1).Replace('\', '/').Split('/', 3)
            if ($parts.Length -lt 3 -or $kinds -notcontains $parts[1]) { continue }
            $id = $parts[0] + ':' + $parts[2].Substring(0, $parts[2].Length - 5)
            $key = $parts[1] + '/' + $id
            try {
                $value = Read-Json $file.FullName
            } catch {
                $problems += @{ kind = $parts[1]; id = $id; path = ''; severity = 'error'; message = 'invalid JSON: ' + $_.Exception.InnerException.Message }
                continue
            }
            if ($value -is [Collections.IDictionary] -and $value.ContainsKey('removed')) {
                $entries.Remove($key)   # tombstone hiding a datapack entry
            } else {
                $entries[$key] = @{ kind = $parts[1]; id = $id; source = 'loose'; json = $value }
            }
        }
    }
    $json.Serialize(@{ entries = @($entries.Values); problems = @($problems) })
}

function Invoke-Api($method, $path, $body) {
    if ($method -eq 'GET' -and $path -eq 'session') {
        return 200, $json.Serialize(@{ protocol = 1; kinds = $kinds; offline = $true })
    }
    if ($method -eq 'GET' -and $path -eq 'registries') {
        $file = Join-Path $here 'registries.json'
        if (Test-Path $file) { return 200, [IO.File]::ReadAllText($file) }
        return 200, '{}'
    }
    if (($method -eq 'GET' -and $path -eq 'content') -or ($method -eq 'POST' -and $path -eq 'reload')) {
        return 200, (Get-Content-Json)
    }
    # content/<kind>/<namespace>/<path...>; the pattern leaves no room for ".." or absolute paths.
    if ($path -cmatch '^content/([a-z]+)/([a-z0-9_.-]+)/([a-z0-9_-][a-z0-9_.-]*(/[a-z0-9_-][a-z0-9_.-]*)*)$' -and $kinds -contains $Matches[1]) {
        $kind = $Matches[1]; $id = $Matches[2] + ':' + $Matches[3]
        $file = Join-Path (Join-Path (Join-Path $root $Matches[2]) $kind) ($Matches[3] + '.json')
        if ($method -eq 'DELETE') {
            if (Test-Path $file) { Remove-Item $file }
            $snapshot = Join-Path $here 'datapack.json'
            if ((Test-Path $snapshot) -and ((Read-Json $snapshot) | Where-Object { $_['kind'] -eq $kind -and $_['id'] -eq $id })) {
                [IO.Directory]::CreateDirectory((Split-Path $file -Parent)) | Out-Null
                [IO.File]::WriteAllText($file, "{`"removed`": true}`n", $utf8)
            }
            return 200, '{}'
        }
        if ($method -eq 'PUT' -or $method -eq 'POST') {
            try { $json.DeserializeObject($body) | Out-Null } catch { return 400, $json.Serialize(@{ error = 'invalid JSON' }) }
            # shortcut: no quest validation offline, the game reports problems on its next load or reload.
            if ($method -eq 'PUT') {
                [IO.Directory]::CreateDirectory((Split-Path $file -Parent)) | Out-Null
                [IO.File]::WriteAllText("$file.tmp", $body.TrimEnd() + "`n", $utf8)
                Move-Item "$file.tmp" $file -Force
            }
            return 200, $json.Serialize(@{ problems = @(); saved = ($method -eq 'PUT') })
        }
    }
    return 404, $json.Serialize(@{ error = 'unknown endpoint' })
}

# Find a free port, then listen on localhost only. HttpListener rejects any other Host header.
$probe = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
$probe.Start(); $port = $probe.LocalEndpoint.Port; $probe.Stop()
$bytes = New-Object byte[] 16
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$token = -join ($bytes | ForEach-Object { $_.ToString('x2') })
$origin = "http://localhost:$port"
$listener = New-Object Net.HttpListener
$listener.Prefixes.Add("$origin/")
$listener.Start()

Write-Host "Solace quest editor (offline) on $origin"
Write-Host "Editing $root"
Write-Host 'Close this window to stop the editor.'
Start-Process "$origin/#token=$token"

while ($listener.IsListening) {
    $context = $listener.GetContext()
    $request = $context.Request
    $response = $context.Response
    try {
        $path = $request.Url.AbsolutePath
        $type = 'application/json; charset=utf-8'
        if (-not $path.StartsWith('/api/')) {
            $status = 200; $type = 'text/html; charset=utf-8'
            $text = [IO.File]::ReadAllText((Join-Path $here 'index.html'))
        } elseif ($request.Headers['Origin'] -and $request.Headers['Origin'] -ne $origin) {
            $status = 403; $text = '{"error":"bad origin"}'
        } elseif ($request.Headers['Authorization'] -cne "Bearer $token") {
            $status = 401; $text = '{"error":"missing or wrong token"}'
        } else {
            $body = (New-Object IO.StreamReader($request.InputStream, $utf8)).ReadToEnd()
            $status, $text = Invoke-Api $request.HttpMethod $path.Substring(5) $body
        }
    } catch {
        $status = 500; $type = 'application/json; charset=utf-8'
        $text = $json.Serialize(@{ error = $_.Exception.Message })
        Write-Host "Error: $($_.Exception.Message)"
    }
    $out = $utf8.GetBytes($text)
    $response.StatusCode = $status
    $response.ContentType = $type
    $response.Headers['Cache-Control'] = 'no-store'
    $response.ContentLength64 = $out.Length
    $response.OutputStream.Write($out, 0, $out.Length)
    $response.Close()
}
