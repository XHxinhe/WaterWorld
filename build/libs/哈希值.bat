@echo off
cd /d X:\AliveWell-1.20.1\build\libs
certutil -hashfile AliveAndWell-mc1.20.1-fabric-5.1.0.jar SHA256
cmd /k