Test Environment Compliance Data & Hardware/Emulator Variant Log

Document Control Reference: SGP-TEST-ENV-COMPLIANCE-v1.0

Associated Baseline: Smart Garden Planner (v20.19)

Rigor Level: DO-178C DAL A / ARP4754A Level A Safety Standards

1. Document Purpose & Scope

This compliance log provides a deterministic, traceable contract detailing the system variations, hardware boundaries, and security workarounds necessary to transition the Smart Garden Planner between a virtual testbed (Android Emulator/AVD) and high-fidelity physical target terminals (e.g., Google Pixel 8 Pro with custom-compiled binaries).

Under DO-178C DAL A guidelines, any software execution pathway that deviates between testing simulators and flight-worthy physical hardware must be fully documented, analyzed for safety-critical hazards, and validated with compensating safeguards.

2. Hardware vs. Emulator Environment Compatibility Matrix

The application's execution environment changes dynamically based on the host platform's physical capabilities. The table below outlines the core hardware and software components that shift behavior between platforms:

Target Subsystem

Virtual Emulator Device Profile (AVD API 34)

Physical Target Terminal Profile (Pixel 8 Pro)

Behavioral Deviation Mitigation & Safeguards

Cryptographic Keystore Enclave

Software-backed fallback emulation. Lack of physical StrongBox container.

Physical Secure Element (StrongBox/TEE) isolations enabled.

Catching StrongBoxUnavailableException and falling back to standard TEE. Tracks warning messages inside the local safety audit log.

Relational SQLite Passphrase

Static passphrase decryption mapped over software enclaves.

Dynamically generated high-entropy passphrases protected by GCM KeyStore keys.

Encrypting localized passphrase bytes ($32\text{ bytes}$) inside private file caches (sgp_db_key.enc).

Inertial Measurement Unit (IMU)

Simulated dummy values or constant zero-matrix streams.

Physical Accelerometer & Gyroscope hardware active at SENSOR_DELAY_FASTEST.

Restricting sensor initialization via a checkCapabilities() pre-flight check. Forces manual text dimension entry on emulators.

Ambient Illumination Sensor

Constant virtual readings (defaults to $150.0\text{ lux}$).

Physical photodiode sensor emitting real-time light exposure metrics.

Implemented a persistent Amber banner indicating low light ($E_{\text{v}} < 10.0\text{ lux}$) to safeguard manual image alignment steps.

Filesystem Storage Space

Expansive host system directory with virtual block devices.

Partition-constrained local file system with strict access controls.

Querying directory sectors via the Android StatFs API. Blocks file writes if free space falls to $\le 5.0\%$.

3. Cryptographic Enclave (StrongBox) Execution Profiles

The core security architecture enforces AES-GCM 256-bit encryption on local database files. The application's key-generation pipeline contains two distinct profiles:

3.1 Strict Hardware StrongBox Profile (Physical Target)

On physical hardware, keys under the alias "SGP_SECURE_ENCLAVE_KEY" are generated inside a dedicated, isolated hardware microprocessor.

Configuration: .setIsStrongBoxBacked(true)

Security Level: Maximum cryptographic protection. Key material is completely isolated from the primary Android OS, blocking side-channel memory attacks.

3.2 Standard TEE Fallback Profile (Emulator Target)

On emulators, requesting physical hardware enclaves raises an immediate StrongBoxUnavailableException.

Configuration: .setIsStrongBoxBacked(false)

Security Level: Emulated cryptographic protection inside standard software-backed Android Keystore enclaves.

Compensating Safeguard: The database files remain encrypted, and a low-severity WARN_ENCLAVE_FALLBACK log is written to sgp_security_audit.log.

4. Inertial Telemetry and Spatial Scale Integration Profiles

4.1 Real-World IMU Integration Profile (Physical Target)

Physical terminals utilize raw motion telemetry vectors polled at maximum hardware polling rates (SENSOR_DELAY_FASTEST) to feed the spatial scale double-integration threads.

Calibration Phase: Runs a $3\text{-second}$ calibration sequence to compute and subtract physical gravity biases ($\vec{g}$).

Sensor Merging: Employs a real-time complementary filter mixing accelerometer and gyroscope values with a coefficient of $\alpha = 0.98$ to bound integration drift to $\sigma \le \pm 5\%$.

4.2 Manual Fallback Integration Profile (Emulator Target)

Because Android emulators cannot simulate high-fidelity 3D motion, attempts to initialize sensor listening on virtual testbeds are intercepted by a pre-flight guard check.

Interception Rule: If accelerometer or gyroscope sensors return null handles, the system sets capability flags to false.

UI Adaptation: The scale selection dropdown Selector-CRT-03 disables the IMU option, forcing the workspace configuration to "Manual Dimensions Entry" via text inputs.

5. Deployment, Synchronization, & Test Configuration Procedures

To execute automated compliance test suites across both environments, developers must configure separate target build variants:

5.1 Emulator Verification Run

Configure your Gradle target to build with emulated mock parameters:

Ensure the AVD simulator target is active via ADB.

Build and install the debug APK profile:

gradlew :app:installDebug


Run the instrumented JUnit test suites to verify local database and ViewModel flow states under software fallbacks.

5.2 Physical Hardware Validation Run

Deploy to target hardware equipped with a physical Secure Element:

Connect the physical Android device via USB debugging.

Validate that secure lock screens and credential gates are active on the device.

Build and execute the release candidate build:

gradlew :app:assembleRelease


Verify that no StrongBoxUnavailableException errors are registered to the security audit logs and that raw telemetry arrays update properly during physical workspace translations.