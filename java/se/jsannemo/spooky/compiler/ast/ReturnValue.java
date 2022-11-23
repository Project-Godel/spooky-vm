package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import java.util.Optional;

@AutoValue
public abstract class ReturnValue {
  public abstract Optional<Expression> value();

  public abstract SourceRange pos();

  public static ReturnValue create(Optional<Expression> value, SourceRange pos) {
    return new AutoValue_ReturnValue(value, pos);
  }
}
