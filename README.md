## Java debugger

Command line java debugger written using the native JDI (JPDA) framework.  
Implemented as a Project in the System Software course at JKU Linz.  

### Usage

Call using:
```sh
java Main <classToCompileAndDebug>
```

`<classToCompileAndDebug>` will first be compiled with `javac -g <classToCompileAndDebug>.java`.  
Then a Debugger instance is allocated, which starts the debuggee VM using the `<classToCompileAndDebug>` argument as class name.


### Commands

| Command | Usage                         | Description                                                                                 |
----------|-------------------------------|---------------------------------------------------------------------------------------------|
| q       | q                             | terminate program and VM                                                                    |
| run     | run                           | resumes the VM                                                                              |
| locals  | locals                        | print all local variables in current frame                                                  |
| globals | globals                       | print all global variables currently visible                                                |
| break   | break {line: int}             | add a breakpoint at line number {line}                                                      |
| lsbreak | lsbreak                       | list all curretly set breakpoints                                                           |
| rmbreak | rmbreak {line: int}           | remove breakpoint at line number {line}                                                     |
| step    | step                          | step **over** to next line                                                                  |
| into    | into                          | step **into** the next instruction (or over)                                                |
| entry   | entry                         | toggle method entry breakpoints (off by default)                                            |
| stack   | stack                         | print stack trace (from current frame)                                                      |
| print   | print {var: str} [idx: int]   | print named local or global varaible (if array, {idx} can be used as an index)              |
| printf  | printf {obj: str} {fld: str}  | print the value of an object's named field                                                  |
| state   | state                         | print the current state of the program, including breakpoints, current line number and code |

{arg} - required  
[arg] - optional  

### Misc
Two test program files `Test.java` and `Classes.java` are included in the repo to test the debugger.
