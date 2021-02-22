package se.jsannemo.spooky.compiler.typecheck;

import com.google.common.collect.ImmutableList;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Utilities for handling structs. */
final class Structs {

  /**
   * Sorts {@code structs} so that a struct only includes as fields structs that appear earlier in
   * the ordering. If no such ordering appears, an empty optional is returned and an error is logged
   * to {@code err}.
   * @return
   */
  static Optional<List<Ast.StructDecl>> topologicalOrder(
      List<Ast.StructDecl> structs, Errors err) {
    HashMap<String, Ast.StructDecl> byName = new HashMap<>();
    structs.forEach(x -> byName.put(x.getName().getName(), x));
    ImmutableList.Builder<Ast.StructDecl> ordering = ImmutableList.builder();
    DfsData dfs = new DfsData(byName);
    for (String name : byName.keySet()) {
      if (!dfsStructs(name, dfs, err)) {
        return Optional.empty();
      }
    }
    return Optional.of(ordering.build());
  }

  private static class DfsData {
    final HashSet<String> onStack = new HashSet<>();
    final HashSet<String> seen = new HashSet<>();
    final ImmutableList.Builder<Ast.StructDecl> ordering = new ImmutableList.Builder<>();
    final HashMap<String, Ast.StructDecl> byName;

    public DfsData(HashMap<String, Ast.StructDecl> byName) {
      this.byName = byName;
    }
  }

  private static boolean dfsStructs(String name, DfsData dfs, Errors err) {
    Ast.StructDecl struct = dfs.byName.get(name);
    if (dfs.onStack.contains(name)) {
      err.error(struct.getPosition(), "Structs have a cyclic dependency on each other.");
      return false;
    }
    if (dfs.seen.contains(name)) {
      return true;
    }
    dfs.onStack.add(name);
    dfs.seen.add(name);
    dfs.ordering.add(struct);
    for (Ast.StructField field : struct.getFieldsList()) {
      if (!dfsStructs(field.getName().getName(), dfs, err)) {
        return false;
      }
    }
    dfs.onStack.remove(name);
    return true;
  }
}
