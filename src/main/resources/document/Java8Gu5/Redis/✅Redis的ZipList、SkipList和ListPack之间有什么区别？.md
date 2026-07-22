# ✅Redis的ZipList、SkipList和ListPack之间有什么区别？

# 典型回答

之所以把他们三个放在一起比较，主要是因为他们都是实现ZSet的主要数据结构。

[✅Redis中的Zset是怎么实现的？](https://www.yuque.com/hollis666/un6qyk/uzqztzuicddlk95c)

**在Redis 7.0之前，ZSet主要靠ZipList&#x548C;****SkipList****&#x5B9E;现**。而在Redis 7.0开始，ZipList已经被ListPack给替代了，也就是说**Redis 7.0之后，ZSet主要靠ListPack和SkipList实现。**

***

这里SkipList是一直在的，先介绍他。

### SkipList

SkipList就是跳表。跳表是一种在链表的基础上增加多层索引的结构。跳表的结构通过多层索引链表来提高查找效率，相比于传统的链表，它能够在对数时间内完成元素的查找、插入和删除。

**跳表的优势就是插入、删除、查找操作都可以在 O(log N) 的时间复杂度内完成。并且支持高效的范围查询，如 **<code>**ZRANGEBYSCORE**</code>**、**<code>**ZRANGE**</code>\*\* 等。\*\*

但是**跳表有个缺点，那就是他的数据结构决定了他的内存占用更高。每个元素有多个指针来维护多层链表，导致内存开销更大。**

### ZipList

而ZipList是一个压缩的数据结构，它的每个元素都是连续存储的，因此内存的使用非常紧凑。**与其他数据结构相比，ZipList在小规模数据存储时显著减少了内存占用。**

但是ZipList也有缺点，正因为他是紧凑的线性结构，所以如果在 ZipList 中查找一个元素时，可能需要遍历整个列表，同理插入和删除操作也是线性的。所以**ZipList的插入、删除和查找操作的时间复杂度通常是 O(N)。相对来说是比较慢的。**

这也是为什么Redis会在元素数量比较少的时候用ZipList，而在数据量大了之后转成SkipList的原因。

[✅ZSet为什么在数据量少的时候用ZipList，而在数据量大的时候转成SkipList？](https://www.yuque.com/hollis666/un6qyk/lk12d5e9kpxg2wkl)

而且，因为ZipList的结构导致了，它在极端情况下可能会出现一种级联更新的问题：

[✅介绍下Redis中的ZipList和他的级联更新问题](https://www.yuque.com/hollis666/un6qyk/pbnk0e7d4dhpzg1v)

所以就有了ListPack

### ListPack

ListPack是 Redis 在 5.0 版本引入的一种新的内存高效数据结构（在Redis 7.0正式替代ZipList用在ZSet中），它是为了解决 `ziplist` 和 `skiplist` 在一些场景下的不足而提出的。

`listpack` 是一种 **适用于小型有序集合、列表和哈希的压缩数据结构**，它比 `ziplist` 更加灵活、适应性更强，并且提供了对大数据量的更好支持。

更重要的事，ListPack通过创新的数据结构方案，避免了级联更新的问题。

[✅Redis中的ListPack是如何解决级联更新问题的？](https://www.yuque.com/hollis666/un6qyk/dy8gkropm3hdlty0)

### 对比总结

| **<font style="color:rgb(64, 64, 64);">特性</font>** | **<font style="color:rgb(64, 64, 64);">SkipList (跳表)</font>** | **<font style="color:rgb(64, 64, 64);">ZipList (压缩列表)</font>** | **<font style="color:rgb(64, 64, 64);">ListPack (紧凑列表)</font>** |
| --- | --- | --- | --- |
| **<font style="color:rgb(64, 64, 64);">设计目标</font>** | <font style="color:rgb(64, 64, 64);">高效范围查询和有序访问</font> | <font style="color:rgb(64, 64, 64);">内存紧凑，减少碎片</font> | <font style="color:rgb(64, 64, 64);">解决级联更新问题</font> |
| **<font style="color:rgb(64, 64, 64);">内存布局</font>** | <font style="color:rgb(64, 64, 64);">多层链表结构</font> | <font style="color:rgb(64, 64, 64);">连续内存块</font> | <font style="color:rgb(64, 64, 64);">连续内存块</font> |
| **<font style="color:rgb(64, 64, 64);">查询复杂度</font>** | **<font style="color:rgb(64, 64, 64);">O(log N)</font>** | <font style="color:rgb(64, 64, 64);">O(n)</font> | <font style="color:rgb(64, 64, 64);">O(n)</font> |
| **<font style="color:rgb(64, 64, 64);">插入复杂度</font>** | <font style="color:rgb(64, 64, 64);">O(log N)</font> | <font style="color:rgb(64, 64, 64);">O(1)~O(n²) (级联更新)</font> | **<font style="color:rgb(64, 64, 64);">O(1)</font>**<font style="color:rgb(64, 64, 64);"> (无级联更新)</font> |
| **<font style="color:rgb(64, 64, 64);">删除复杂度</font>** | <font style="color:rgb(64, 64, 64);">O(log N)</font> | <font style="color:rgb(64, 64, 64);">O(1)~O(n²) (级联更新)</font> | **<font style="color:rgb(64, 64, 64);">O(1) </font>**<font style="color:rgb(64, 64, 64);">(无级联更新)</font> |
| **<font style="color:rgb(64, 64, 64);">内存占用</font>** | <font style="color:rgb(64, 64, 64);">高 (有指针开销)</font> | <font style="color:rgb(64, 64, 64);">低</font> | **<font style="color:rgb(64, 64, 64);">极低</font>** |
| **<font style="color:rgb(64, 64, 64);">版本支持</font>** | <font style="color:rgb(64, 64, 64);">所有版本</font> | <font style="color:rgb(64, 64, 64);">Redis ≤6.2</font> | **<font style="color:rgb(64, 64, 64);">Redis ≥5.0 (7.0+默认)</font>** |
| **<font style="color:rgb(64, 64, 64);">核心优势</font>** | <font style="color:rgb(64, 64, 64);">高效有序访问</font> | <font style="color:rgb(64, 64, 64);">小数据内存优化</font> | <font style="color:rgb(64, 64, 64);">无级联更新+内存紧凑</font> |
| **<font style="color:rgb(64, 64, 64);">主要缺点</font>** | **<font style="color:rgb(64, 64, 64);">内存占用高</font>** | **<font style="color:rgb(64, 64, 64);">级联更新</font>** | <font style="color:rgb(64, 64, 64);">范围查询效率低</font> |
| **<font style="color:rgb(64, 64, 64);">应用场景</font>** | <font style="color:rgb(64, 64, 64);">有序集合(ZSet)</font> | <font style="color:rgb(64, 64, 64);">小规模Hash/Set/ZSet</font> | <font style="color:rgb(64, 64, 64);">全类型小规模存储</font> |


> 更新: 2025-07-18 21:33:19  
> 原文: <https://www.yuque.com/hollis666/un6qyk/bn7lc5eg9dg151xm>