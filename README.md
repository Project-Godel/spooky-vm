![Spoooky!](https://github.com/jsannemo/spooky-vm/blob/master/spook.png?raw=true)

So spooky.

## Usage:
```
java --enable-preview spooky.jar compile source.spooky exec.spook
java --enable-preview spooky.jar run exec.spook
```

## Example programs
```scala
extern printInt(i: Int)
extern print(ch: Int)

func isPrime(n: Int) -> Int {
  p: Int = 1;
  for (i: Int = 2; i * i <= n; i = i + 1) {
    if (n % i == 0) {
      return false;
    }
  }
  return true;
}

func main() {
  for (n: Int = 90; n < 100; n = n + 1) {
    printInt(n);
    print(32);
    printInt(isPrime(n));
    print(10);
  }
}
```
