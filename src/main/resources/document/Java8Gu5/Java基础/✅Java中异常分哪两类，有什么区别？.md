# ✅Java中异常分哪两类，有什么区别？

# 典型回答

Java中的异常，主要可以分为两大类，即受检异常（checked exception）和 非受检异常（unchecked exception）

对于**受检异常**来说，如果一个方法在声明的过程中证明了其要有受检异常抛出：

`public void test() throws Exception{}`

那么，当我们在程序中调用他的时候，一定要对该异常进行处理（捕获或者向上抛出），否则是无法编译通过的。这是一种强制规范。

这种异常在IO操作中比较多。比如FileNotFoundException ，当我们使用IO流处理一个文件的时候，有一种特殊情况，就是文件不存在，所以，在文件处理的接口定义时他会显示抛出FileNotFoundException，起目的就是告诉这个方法的调用者，我这个方法不保证一定可以成功，是有可能找不到对应的文件的，你要明确的对这种情况做特殊处理哦。

所以说，当我们希望我们的方法调用者，明确的处理一些特殊情况的时候，就应该使用受检异常。

对于**非受检异常**来说，一般是运行时异常，继承自RuntimeException。在编写代码的时候，不需要显式的捕获，但是如果不捕获，在运行期如果发生异常就会中断程序的执行。

这种异常一般可以理解为是代码原因导致的。比如发生空指针、数组越界等。所以，只要代码写的没问题，这些异常都是可以避免的。也就不需要我们显示的进行处理。

试想一下，如果你要对所有可能发生空指针的地方做异常处理的话，那相当于你的所有代码都需要做这件事。

# 知识扩展

## \*\*<font style="color:rgb(38, 38, 38);">什么是</font>\*\*Throwable

Throwable是java中最顶级的异常类，继承Object，实现了序列化接口，有两个重要的子类：Exception和 Error，二者都是 Java 异常处理的重要子类，各自都包含大量子类。

## Error和Exception的区别和联系

error表示系统级的错误，是java运行环境内部错误或者硬件问题，不能指望程序来处理这样的问题，除了退出运行外别无选择，它是Java虚拟机抛出的。如OutOfMemoryError、StackOverflowError�这两种常见的错误都是ERROR。

exception 表示程序需要捕捉、需要处理的异常，是由与程序设计的不完善而出现的问题，程序必须处理的问题。分为RuntimeException和其他异常

## 请列举几个常用的RuntimeException。

这个题目，其实面试官考的还挺多的，主要是考察面试者实战经验是否丰富，所以常见的RuntimeException要能回答的尽量多回答一些。

AnnotationTypeMismatchException, ArithmeticException, ArrayStoreException, BufferOverflowException, BufferUnderflowException, CannotRedoException, CannotUndoException, ClassCastException, CMMException, ConcurrentModificationException, DataBindingException, DOMException, EmptyStackException, EnumConstantNotPresentException, EventException, FileSystemAlreadyExistsException, FileSystemNotFoundException, IllegalArgumentException, IllegalMonitorStateException, IllegalPathStateException, IllegalStateException, IllformedLocaleException, ImagingOpException, IncompleteAnnotationException, IndexOutOfBoundsException, JMRuntimeException, LSException, MalformedParameterizedTypeException, MirroredTypesException, MissingResourceException, NegativeArraySizeException, NoSuchElementException, NoSuchMechanismException, NullPointerException, ProfileDataException, ProviderException, ProviderNotFoundException, RasterFormatException, RejectedExecutionException, SecurityException, SystemException, TypeConstraintException, TypeNotPresentException, UndeclaredThrowableException, UnknownEntityException, UnmodifiableSetException, UnsupportedOperationException, WebServiceException, WrongMethodTypeException

## 说一说个Java异常处理相关的几个关键字，以及简单用法。

throws、throw、try、catch、finally

1. try用来指定一块预防所有异常的程序；
2. catch子句紧跟在try块后面，用来指定你想要捕获的异常的类型；
3. finally为确保一段代码不管发生什么异常状况都要被执行；
4. throw语句用来明确地抛出一个异常；
5. throws用来声明一个方法可能抛出的各种异常；

[✅try中return A，catch中return B，finally中return C，最终返回值是什么？](https://www.yuque.com/hollis666/un6qyk/ltw8ngs7yntrdk3a)

[✅finally中代码一定会执行吗？](https://www.yuque.com/hollis666/un6qyk/rs846vlvpa7dwe3v)

## 什么是自定义异常，如何使用自定义异常？

自定义异常就是开发人员自己定义的异常，一般通过继承Exception的子类的方式实现。

编写自定义异常类实际上是继承一个API标准异常类，用新定义的异常处理信息覆盖原有信息的过程。

这种用法在Web开发中也比较常见，一般可以用来自定义业务异常。如余额不足、重复提交等。这种自定义异常有业务含义，更容易让上层理解和处理。

## 为什么不建议使用异常控制业务流程

[✅为什么不建议使用异常控制业务流程](https://www.yuque.com/hollis666/un6qyk/kgodgo19faudkgt2)


> 更新: 2024-05-18 12:00:44  
> 原文: <https://www.yuque.com/hollis666/un6qyk/dx3i8a>