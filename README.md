![Spoooky!](https://github.com/jsannemo/spooky-vm/blob/master/spook.png?raw=true)

Spooky is a simple programming language with an accompanying compiler and virtual machine.

Current build: ![last build status](https://circleci.com/gh/Project-Godel/spooky-vm.svg?style=shield)

## Requirements
To run the compiler, you need to have Java 11 or later installed.

## Usage
To compile the source `source.spooky` into the compiled file `exec.spook`, run
```
java -jar spooky.jar compile source.spooky exec.spook
```

To execute the program, run
```
java -jar spooky.jar run exec.spook
```

## Language features
The language is very bare bones right now, only supporting basics such as variables, functions, loops, conditionals and some simple expressions.
While the VM itself is pretty fixed in structure, contributions to the language itself are very welcome.
In particular, features such as

- more operators, such as `++`
- arrays
- character literals
- string literals

would be nice.

## External functions
When executing programs, a set of external functions may optionally be provided.
These are declared as `extern` functions in your source and provided by the execution environment.

## Example programs
```scala
extern printInt(i: Int)
extern print(ch: Char)

func isPrime(n: Int) -> Boolean {
  p: Int = 1;
  for (i: Int = 2; i * i <= n; i += 1) {
    if (n % i == 0) {
      return false;
    }
  }
  return true;
}

func main() {
  for (n: Int = 90; n < 100; n += 1) {
    printInt(n);
    print(' ');
    if (isPrime(n)) {
        printInt(1);
    } else {
        printInt(0);
    }
    print('\n');
  }
}
```
