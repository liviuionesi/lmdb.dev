# Test Output Configuration

## Overview

Test output has been configured for clean, readable results in both command-line and IDE environments.

## Default Behavior

By default, tests run silently and only show:
- **Failed tests** (with full stack traces)
- **Skipped tests**
- **Summary at the end** with total counts

### Example Output

```
> Task :backend:shared-library:test

=================================================================
|  Results: SUCCESS (96 tests, 96 passed, 0 failed, 0 skipped)  |
=================================================================
```

## Viewing All Tests

To see all test execution details, use the `--info` flag:

```bash
./gradlew test --info
```

This will show:
- All test methods as they execute
- Passed, failed, and skipped tests
- Full exception details for failures

## IDE Configuration

### IntelliJ IDEA

The IDE may still show verbose output in its test runner. To improve readability:

1. **Run Configuration Settings:**
   - Go to Run → Edit Configurations
   - Select your test configuration
   - Under "JUnit" settings, enable:
     - ✅ "Show console when standard output changes"
     - ✅ "Show console when standard error changes"
   - Set "Output format" to "Plain text" (not "TeamCity")

2. **Test Runner Settings:**
   - File → Settings → Build, Execution, Deployment → Build Tools → Gradle
   - Set "Run tests using" to "Gradle Test Runner" (instead of IntelliJ IDEA)
   - This uses Gradle's cleaner output format

3. **Alternative: Use Gradle Tool Window:**
   - View → Tool Windows → Gradle
   - Run tests from the Gradle tool window
   - Output will use Gradle's configured format

### VS Code

VS Code respects Gradle's test output configuration automatically.

## Configuration Details

The test output is configured in `build.gradle`:

```groovy
tasks.withType(Test) {
    useJUnitPlatform()
    
    testLogging {
        // Only show failures and skipped tests by default
        events = ['failed', 'skipped']
        exceptionFormat = 'full'
        showStandardStreams = false
        showCauses = true
        showStackTraces = true
        
        // Show all tests when using --info flag
        info {
            events = ['started', 'passed', 'skipped', 'failed']
            exceptionFormat = 'full'
        }
        
        // Summary at the end
        afterSuite { desc, result ->
            if (!desc.parent) {
                def output = "Results: ${result.resultType} (...)"
                // ... formatted summary
            }
        }
    }
}
```

## Benefits

✅ **Less noise** - Only failures shown by default  
✅ **Clear summary** - Easy to see test results at a glance  
✅ **Full details** - Use `--info` when needed  
✅ **IDE friendly** - Works with IntelliJ and VS Code  

## Troubleshooting

### Still seeing verbose output in IDE?

1. Check if you're using IntelliJ's test runner vs Gradle's
2. Try running from Gradle tool window instead
3. Configure IDE to use Gradle test runner (see above)

### Want more verbose output by default?

Edit `build.gradle` and change:
```groovy
events = ['failed', 'skipped']
```
to:
```groovy
events = ['passed', 'skipped', 'failed']
```

---

**Last Updated:** 2025-11-16

