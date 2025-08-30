package org.geekden;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class MainApplication implements QuarkusApplication {

  @Override
  public int run(String... args) throws Exception {
    System.out.println("Hello World!");
    return 0;
  }

  public static void main(String[] args) {
    Quarkus.run(MainApplication.class, args);
  }
}