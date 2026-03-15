package io.github.dornol.filekit.validator;

public interface MessageConverter {

    String convert(String key, Object... args);

}
