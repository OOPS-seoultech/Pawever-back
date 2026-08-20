package com.pawever.backend.global.config;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 엔티티에 있는 컬럼이 마이그레이션에도 있는지 본다.
 *
 * 운영은 ddl-auto: none 이라 컬럼이 없어도 기동한다. 그 테이블을 건드리는 쿼리가
 * 돌 때가 되어서야 실패하고, 그때는 이미 배포된 뒤다. 테스트는 H2 + create-drop 에
 * Flyway 를 끄고 돌아서 엔티티로 스키마를 만들어 쓴다. 그래서 마이그레이션을
 * 빠뜨려도 지금까지는 아무 데서도 걸리지 않았다. 실제로 한 번 그렇게 나갔다.
 *
 * 타입·제약까지 보지는 않는다. 컬럼이 아예 없는 경우만 잡는다. 그 하나가
 * 배포 후에야 드러나는 실패의 대부분이다.
 */
class MigrationCoversEntityColumnsTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final String ENTITY_PACKAGE = "com.pawever.backend";

    /**
     * 마이그레이션 밖에서 만들어지는 컬럼.
     *
     * 여기에 이름을 올리는 것은 "마이그레이션 없이 둔다"는 선언이다.
     * 새 컬럼을 급히 통과시키려고 올리면 이 테스트가 있으나 마나 해진다.
     */
    private static final Map<String, String> EXEMPT = Map.of();

    @Test
    void 엔티티_컬럼은_모두_마이그레이션에_있다() throws IOException {
        String migrations = readMigrations().toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();

        for (Class<?> entity : findEntities()) {
            for (String column : columnsOf(entity)) {
                String key = entity.getSimpleName() + "." + column;
                if (EXEMPT.containsKey(key)) {
                    continue;
                }
                if (!containsColumn(migrations, column)) {
                    missing.add(key);
                }
            }
        }

        if (!missing.isEmpty()) {
            throw new AssertionError(
                    "마이그레이션에 없는 컬럼이 있습니다. db/migration 에 추가하세요:\n  "
                            + String.join("\n  ", missing)
            );
        }
    }

    private String readMigrations() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            List<Path> sql = files.filter(path -> path.toString().endsWith(".sql")).toList();
            if (sql.isEmpty()) {
                throw new AssertionError("마이그레이션 파일을 찾지 못했습니다: " + MIGRATION_DIR);
            }
            StringBuilder joined = new StringBuilder();
            for (Path path : sql) {
                joined.append(Files.readString(path)).append('\n');
            }
            return joined.toString();
        }
    }

    private List<Class<?>> findEntities() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> entities = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            try {
                entities.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException exception) {
                throw new AssertionError(exception);
            }
        }
        if (entities.isEmpty()) {
            throw new AssertionError("엔티티를 찾지 못했습니다: " + ENTITY_PACKAGE);
        }
        return entities;
    }

    /** 상속받은 필드(BaseTimeEntity 의 created_at 등)까지 훑는다. */
    private List<String> columnsOf(Class<?> entity) {
        List<String> columns = new ArrayList<>();
        for (Class<?> type = entity; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String column = columnNameOf(field);
                if (column != null) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    private String columnNameOf(Field field) {
        if (Modifier.isStatic(field.getModifiers())
                || field.isAnnotationPresent(Transient.class)
                || Collection.class.isAssignableFrom(field.getType())) {
            return null;
        }

        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isBlank()) {
            return joinColumn.name();
        }

        jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }

        // 연관관계는 이름 뒤에 _id 가 붙는다.
        if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
            return toSnake(field.getName()) + "_id";
        }
        return toSnake(field.getName());
    }

    private String toSnake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** 다른 컬럼 이름에 겹쳐 걸리지 않도록 낱말 경계로 본다. */
    private boolean containsColumn(String migrations, String column) {
        return migrations.matches("(?s).*\\b" + java.util.regex.Pattern.quote(column) + "\\b.*");
    }
}
