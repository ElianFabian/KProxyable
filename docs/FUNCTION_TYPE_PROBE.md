# Sondeo KSP y contrato de tipos función

Combinación comprobada: Kotlin 2.0.21 y KSP 2.0.21-1.0.28. Este documento separa las observaciones del compilador de las reglas públicas que aplica KProxyable. No establece una versión mínima de KSP.

## Observaciones sin normalizar

| Caso | Resultado observado en KSP |
| --- | --- |
| `Function1<in Int, out String>` | Conserva CONTRAVARIANT y COVARIANT |
| `@ExtensionFunctionType Function2<String, Int, Boolean>` | Conserva el marcador de receptor; la firma tiene receptor String, parámetro Int y resultado Boolean |
| `(input: Int) -> String` | Expone kotlin.ParameterName en el tipo del parámetro directo |
| `(@ParameterName("receiver") String).(input: Int) -> String` | Expone ParameterName tanto en el receptor como en el parámetro |
| `(input: List<@ParameterName("nested") Int>) -> String` | Expone el nombre directo en List y la anotación anidada en Int |
| `(outer: Int) -> (inner: Int) -> Unit` | Cada nodo función tiene ParameterName en sus entradas directas |
| `List<@ParameterName("element") Int>` | La anotación está en el argumento Int de una colección ordinaria |
| Función de 23 parámetros | Declaración kotlin.Function23 con 24 argumentos, incluido el resultado |
| Función suspend de 23 parámetros | Declaración kotlin.coroutines.SuspendFunction23 con 24 argumentos; no añade Continuation |
| `Function<Int>` | isFunctionType e isSuspendFunctionType son false |
| `KFunction1<Int, String>` | isFunctionType es true |
| `KSuspendFunction1<Int, String>` | isSuspendFunctionType es true |
| Clase propia Function1 | Los indicadores de función son false |
| `T : () -> Unit` | El uso de T es una referencia a parámetro; la función aparece en su bound |

Los indicadores públicos de KSType no distinguen las familias regulares de las reflexivas. La API pública de esta versión, inspeccionada localmente, no expone un kind de familia adicional. El generador compara por ello las declaraciones con nombres cualificados exactos de las familias kotlin.FunctionN y kotlin.coroutines.SuspendFunctionN.

El sondeo previo también confirmó que los literales Kotlin Function22::class, Function23::class y Function::class compilan en los siete entornos. En JVM, javap mostró que Function23::class se transforma en kotlin.jvm.functions.FunctionN, mientras Function22::class utiliza kotlin.jvm.functions.Function22. El nombre y la representación textual de KClass no son una fuente portable de aridad.

## Pruebas de caracterización actuales

El source set `kspProbe` del módulo kproxyable-processor contiene un procesador exclusivo de tests. Lee los símbolos originales de FunctionTypeProbe y genera datos expect/actual para cada compilación de sample-kmp. No depende de TypeDescriptorGenerator ni de la fábrica y no se empaqueta en el jar publicado del procesador.

`KspFunctionCharacterizationTest` comprueba esos datos directamente. Incluye cuatro pruebas: indicadores y familias, proyecciones y marcador de receptor, ubicación de ParameterName, y aridad 23 normal/suspend. Un cambio futuro de KSP puede hacer fallar estas pruebas aunque el contrato público siga siendo implementable; ese fallo exige revisar el procesamiento del símbolo.

`KotlinFunctionFamilyTest`, en el módulo del procesador, prueba por separado la política de coincidencia exacta: acepta ambas familias sin frontera de 22 y rechaza reflexión, Function<R>, nombres de otros paquetes, prefijos y sufijos.

## Contrato implementado

La representación y ejemplos completos están en [Descriptores de tipos](TYPE_DESCRIPTORS.md#funciones-y-receptores).

- Flags presentes en todos los tipos función Kotlin regulares/suspend, incluso cuando ambos valores son false.
- Un marcador lógico Function::class con flags. Sin flags puede describir el supertipo corriente Function<R>.
- KFunctionN, KSuspendFunctionN, SAM y clases propias conservan sus clasificadores sin flags ni functionType.
- Una sola lista de argumentos lógicos: receptor opcional, parámetros y retorno. Sin Continuation implícito.
- Constructor/copy estrictos; fábrica que normaliza proyecciones redundantes y rechaza las conflictivas.
- Estrellas conservadas con flags; la vista functionType devuelve null si hay alguna.
- ExtensionFunctionType representada por hasReceiver. ParameterName eliminado solo de entradas directas en cada nodo función, recursivamente. Se conservan las anotaciones anidadas en clasificadores ordinarios.
- La fábrica y el generador producen descriptores idénticos para flecha, FunctionN, proyecciones redundantes y alias equivalentes. La igualdad del constructor crudo sigue siendo estructural y no infiere flags omitidos.

`FunctionTypeContractTest` comprueba el resultado de proxies reales, incluyendo alias genéricos con Int y String, alias suspend/nullable, funciones anidadas, reflexivos heredados con sustitución de T por Int, la clase propia Function1 y aridad 23 normal/suspend. También invoca un callback suspend con receptor recibido por el handler, verificando que conserva el objeto original.

`FunctionDescriptorFactoryTest` cubre la construcción manual, estrellas, copy, invariantes y filtros contextuales. La conservación de anotaciones se comprueba mediante las instancias originales; no depende de que anotaciones recién construidas sean iguales por valor en todos los backends.

## Migración

El generador anterior construía functionType para KFunction1 y KSuspendFunction1. Ese comportamiento se cambia deliberadamente: ahora son clasificadores ordinarios. Los consumidores de classifier == Function1::class deben consultar functionFlags y la firma lógica. functionType deja de ser un argumento del constructor y pasa a ser una vista calculada.

El cambio de owner de propiedades, la retirada de receiverType, la sustitución pública y la normalización según bounds ya se han implementado; véase [Operaciones de tipos](TYPE_OPERATIONS.md).

## Ejecución

Los tests previos de tipos, dynamic y enlace entre módulos se ejecutan con estas suites. Windows cubre JVM, JS y Wasm en Node y Edge headless, además de Native mingwX64. Ubuntu WSL cubre Native Linux x64. No se ejecutan Linux ARM64, Apple ni la matriz adicional de versiones de Kotlin/KSP.

Logs de la verificación actual: `build/all-windows-function-contract.log` y `build/linux-function-contract.log`. Resultados XML: los directorios build/test-results de los módulos. Resultado: **235 ejecuciones correctas**, sin fallos, errores ni tests omitidos: 202 en Windows y 33 en Native Linux x64. Los siete entornos ejecutan cuatro tests de caracterización KSP, doce de contrato público y cinco de fábrica. Los casos de aridad 23 normal y suspend pasan en cada entorno.

| Entorno | Total de tests |
| --- | ---: |
| JVM | 33 |
| JS Node | 35 |
| Wasm Node | 33 |
| Native Windows x64 | 33 |
| JS Edge headless | 34 |
| Wasm Edge headless | 32 |
| Procesador JUnit | 2 |
| Native Linux x64 | 33 |

Como antecedente, el sondeo previo al refactor tenía ocho pruebas en siete entornos (56 ejecuciones); sumado a las pruebas anteriores daba 142. Esa cifra histórica no describe el nuevo contrato.



## Verificación posterior de operaciones

La matriz completa con sustitución, scopes, nulabilidad y adaptadores alcanza 280 casos correctos. El [informe de operaciones](TYPE_OPERATIONS.md) recoge el detalle por entorno y los logs actuales.
