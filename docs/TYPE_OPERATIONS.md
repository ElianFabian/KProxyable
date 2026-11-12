# Operaciones de tipos y ejemplo de adaptadores

Las operaciones públicas viven en TypeOperations.kt y funcionan en commonMain. Conservan los ids de las declaraciones y separan sustitución estructural de normalización contextual.

## Sustitución simultánea

```kotlin
val id = TypeParameterId("Repository", 0)
val t = TypeDescriptor(typeParameter = id)
val result = t.substitute(mapOf(id to TypeDescriptor(String::class)))
```

El reemplazo se aplica una vez: T → List<T> devuelve List<T>, sin volver a visitar el T introducido. Se recorren argumentos Typed y se conservan estrellas y flags de funciones. Las anotaciones del reemplazo preceden a las del uso. No se resuelve subtipado ni se sustituye una estrella por Any?.

La marca externa prevalece salvo UNMARKED, que hereda la del reemplazo. T? con T → U produce U?; T & Any con T → String? produce String. Una sustitución por dynamic conserva DYNAMIC sin nullability independiente, incluso bajo una marca externa. Los tests JS compilan usos genéricos nullable y definitivamente no nulos con dynamic. El test JVM contrasta T & Any con un resultado Java de plataforma y comprueba el descriptor no nullable.

Hay sobrecargas para TypeParameterDescriptor, FunctionDescriptor y PropertyDescriptor. También sustituyen bounds y mantienen los ids. No filtran parámetros declarados: el mapa expresa explícitamente las referencias que el consumidor quiere reemplazar.

## Scopes y garantías de nulabilidad

FunctionDescriptor.typeParameterScope() reúne parámetros del owner y del método por id. PropertyDescriptor ofrece la consulta de su owner. Un scope contiene declaraciones; no deduce los argumentos concretos usados al crear una instancia mediante KClass.

effectiveNullability(scope) devuelve NOT_NULL para un clasificador no nullable, una intersección no nula o un parámetro con algún bound que excluya null. NULLABLE corresponde a una marca explícita nullable. UNKNOWN representa parámetros sin resolver, plataforma y dynamic. Bounds vacíos se tratan como Any? para esta consulta. Los ciclos manuales se protegen con ids visitados; otros bounds no nulos pueden aportar una garantía incluso si hay un ciclo.

normalizeNullability(scope) reduce NOT_NULL a UNMARKED en un parámetro cuyos bounds ya excluyen null. Recorre argumentos conservando estrellas y flags. Las sobrecargas para miembros normalizan también bounds y owner con un único scope. Es idempotente según estas reglas; la igualdad de anotaciones sigue dependiendo de sus instancias en cada backend.

## Generación y herencia

El generador compone mapas en cada arista de la jerarquía. Para Child : Mid<String>, Mid<X> : Parent<List<X>>, obtiene T → List<String>. KSP resuelve usos mediante asMemberOf; el mapa compuesto sustituye también los bounds originales de los parámetros del método. Después normaliza el descriptor completo. El R declarado por Parent conserva su id aunque su bound se especialice.

TypeOperationsTest comprueba esa jerarquía con fun <R : T>, así como sustitución simultánea, estrellas, reflexivos, callbacks suspendidos, scopes, ciclos y normalización. Los handlers de los tests anteriores verifican también que los descriptores recibidos ya están normalizados.

## Propiedades

PropertyDescriptor.owner ahora es obligatorio y no nullable. Se retira receiverType de las propiedades: el handler no tiene un argumento de receptor para getter/setter. Los receptores de métodos y tipos función permanecen disponibles.

El procesador rechaza propiedades de extensión con el diagnóstico KProxyable does not support extension properties. La prueba negativa reproducible está en scratch/test_extension_property_diagnostic.ps1. Copia un fixture temporal, ejecuta KSP, exige ese diagnóstico y retira el fixture en finally sin sobrescribir archivos existentes.

## Biblioteca de adaptadores de ejemplo

sample-kmp/src/commonMain/kotlin/com/elianfabian/kproxyable/sample/StorageAdapterExample.kt contiene una biblioteca mínima ejecutable:

- AdaptedUserStore declara findById y recibe el proxy generado.
- ColumnAdapter define conversiones de columna; UserIdColumnAdapter recibe un prefijo por constructor.
- UserStoreHandler recibe UserRows y el adaptador como dependencias explícitas.
- findById llama a encode antes de consultar y a decode al construir StoredUser. Una fila ausente devuelve null y los errores de conversión se propagan.

StorageAdapterTest invoca el proxy real y comprueba conversiones en ambas direcciones, inyección, fila ausente y error de conversión. No usa reflexión para construir el adaptador. Es una demostración de composición sobre KProxyable; no implementa SQL ni un contrato universal de selección de adaptadores para bibliotecas distintas.

## Migración

Recompilar runtime, procesador y consumidores juntos. Los constructores manuales de propiedades deben proporcionar owner y eliminar receiverType. Las nuevas operaciones son extensiones importables desde com.elianfabian.kproxyable. Usar sustituir → normalizar cuando existan argumentos concretos; no interpretar UNKNOWN como un booleano nullable.

La combinación comprobada continúa siendo Kotlin 2.0.21 y KSP 2.0.21-1.0.28. Apple, Linux ARM64 y la matriz de versiones requieren verificaciones independientes.

## Resultado de la verificación completa

Con Kotlin 2.0.21 y KSP 2.0.21-1.0.28 pasan **280 ejecuciones**, sin fallos, errores ni tests omitidos: 241 en Windows y 39 en Native Linux x64. Se forzaron las 140 tareas de Windows y las 28 de Linux mediante --rerun-tasks. Los módulos sin fuentes de test no cuentan como casos ejecutados.

| Entorno | Casos |
| --- | ---: |
| jvmTest | 40 |
| jsNodeTest | 42 |
| wasmJsNodeTest | 39 |
| mingwX64Test | 39 |
| jsBrowserTest | 41 |
| wasmJsBrowserTest | 38 |
| test | 2 |
| linuxX64Test | 39 |

Además pasa la prueba negativa de compilación de propiedades de extensión, ejecutada mediante scratch/test_extension_property_diagnostic.ps1. El fixture temporal se retira al terminar. La comprobación exige el diagnóstico específico, no cualquier fallo del compilador.

Las pruebas incluyen propiedades heredadas de lectura/escritura y su owner, composición de dos niveles de herencia y bounds de métodos, sustitución simultánea, scopes, ciclos defensivos, normalización idempotente, funciones y reflexivos, dynamic en JS, plataforma Java en JVM y conversiones reales mediante adaptadores inyectados. Los tests anteriores de metadatos y enlaces entre módulos siguen pasando.

Logs: build/all-windows-type-operations.log, build/linux-type-operations.log y build/extension-property-negative.log. Los XML están en build/test-results de cada módulo. Apple, Linux ARM64 y otras combinaciones de Kotlin/KSP no se han ejecutado en esta matriz.
