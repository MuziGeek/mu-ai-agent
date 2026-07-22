# ✅Redis中的setnx和setex有啥区别？

# 典型回答

这个其实只要知道了他们是什么东西的缩写就容易回答了。

SETNX ，SET if Not eXists ， 只有键不存在时才设置值

* 不能设置过期时间

SETEX  ,   SET with EXpiration，  设置值并指定过期时间

* 无条件进行设置，并带有过期时间

#### `SETNX` 示例：

```plain
SETNX holliskey "hello"
```

* 如果 `holliskey` 不存在，就设置为 `"hello"`，\*\*返回 \*\*<code>**1**</code>
* 如果 `holliskey` 已存在，不做修改，\*\*返回 \*\*<code>**0**</code>

#### `SETEX` 示例：

```plain
SETEX holliskey 60 "hello"
```

* \*\*无论 **<code>**holliskey**</code>** 是否存在，都会设置为 \*\*<code>**"hello"**</code>
* 并设置过期时间为 60 秒（TTL）

**在使用场景上，setnx常用于分布式锁的实现，或者幂等处理上面。而setex常用于缓存的过期控制上面。**

# 扩展知识

## 只在键不存在时设置，并设置过期时间

前面提到了，**SETNX是不能设置超时时间的**，那么想要实现“只在键不存在时设置，并设置过期时间 ”需要通过EXPIRE命令来配合才行，即：

```plain
SETNX hollis_key "666"
EXPIRE hollis_key 10
```

但是这样做就不是原子命令了，需要配合事务或者lua脚本才行。

或者**直接使用SET命令也能实现**：

```plain
SET hollis_key "666" NX EX 10
```

***


> 更新: 2025-06-14 13:36:26  
> 原文: <https://www.yuque.com/hollis666/un6qyk/uaowmy30rb6778mf>