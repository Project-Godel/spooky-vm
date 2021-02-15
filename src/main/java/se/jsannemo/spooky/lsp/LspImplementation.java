package se.jsannemo.spooky.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.concurrent.CompletableFuture;

public class LspImplementation implements LanguageServer {
  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
    return null;
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return null;
  }

  @Override
  public void exit() {}

  @Override
  public TextDocumentService getTextDocumentService() {
    return null;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return null;
  }
}
