## Tutorial

Specmonstah was born out of a need to replace brittle, repetitive code
for creating deeply-nested hierarchies of data in unit tests. This
tutorial will show you how to use Specmonstah specifically for this
use case. Along the way you'll learn how to make the most of
Specmonstah by understanding how it's not implemented to support
writing unit tests per se, but to support the more fundamental
operations of generating and manipulating entity graphs.

In trying to figure out how to explain this I had the thought,
"Specmonstah is all about _data_ and _the stuff you can do with that
data_. It felt super profound, like I had gotten a direct glimpse of
the underlying structure of the cosmos. Real "shower thoughts" moment.

We'll start with a high-level overview of how Specmonstah's `data` and
`operations` support the overall goal of aiding testing by generating
and inserting records in a database in dependency order.

### Data & Operations

Specmonstah's data and operations can be summarized as:

* Data
  * ent db
    * schema
    * ent graph
    * ent attrs
* Operations
  * add ents
  * add ent attrs (ent visitation)
  * create views of the ent graph

To explain each of these bullets, we'll work through the following
questions:

* How does Specmonstah generate data for database insertion?
* How does Specmonstah insert records in the correct order?

Data generation happens in two phases:

1. You _add ents_ to an _ent db_'s _ent graph_
2. You add the data generated by clojure.spec to each ent as an _ent
   attr_

Let's unpack this.

Specmonstah works by generating an _ent graph_. Say you want to
generate and insert three `todo`s that references a `todo-list`, where
the `todo`s and the `todo-list` all reference a `user`.  Specmonstah
accomplishes this by first creating a graph like this:

![Simple todo example](docs/todo-example.png)

In the graph above, we call the `:todo`, `:todo-list`, and `:user`
nodes _ent types_ and the rest _ents_. We use these names in
Specmonstah to indicate that these graph nodes take on a particular
meaning in Specmonstah:

**Ent type.** An ent type is analogous to a relation in a relational
database, or a class in object-oriented programming. It differs in
that relations and classes define all the attributes of their
instances, whereas ent types don't. Ent types define how instances are
related to each other. For example, a Todo schema might include a
`:description` attribute, but the `:todo` ent type doesn't. The
`:todo` ent type _does_ specify that a `:todo` instances reference
`:todo-list` instances.

Ent types are represented as nodes in the ent graph (let's abbreviate
that with _EG_), with directed edges going from ent types to their
instances. It's rare that you'll interact with ent types directly.

**Ent.** An ent is an instance of an ent type. Ents have names (`:t0,
:u0`, etc), and reference other ents. They're represented as nodes in
the EG, with directed edges going from ents to the ents they
reference; there's a directed edge from `:tl0` to `:u0` because `:tl0`
references `:u0`. The graph's topology is used to ensure that `:u0`
gets inserted before `:tl0`.

In creating the above graph, we would say that we _add ents_ to an
_ent db_. An ent db is a map that contains an ent graph.

The ent db also contains a _schema_. The schema describes how ents of
different types refer to each other, and it's used to construct the
directed edges between ents.

So that's the first step of data generation: You _add ents_ to an _ent
db_'s _ent graph_. After that, you add the data generated by
clojure.spec to each ent as an _ent attr_.

For example, the ents `:u0` and `:tl0` are not maps. They're just a
graph node, and as such they cannot be inserted in a
database. Specmonstah uses clojure.spec to generate data for `:u0` and
`:tl0` and then associates the data with `:u0` and `:tl0` as an _ent
attr_. You can think of this as being represented using a map like
this, with `:spec-gen` as the ent attr:

```
{:u0  {:spec-gen {:username "billy"
                  :id       1}}
 :tl0 {:spec-gen {:id       2
                  :owner-id 1
                  :title    "my todaloo list"}}}
```

The process of adding ent attrs is called _visitation_. Visiting ent
nodes is kind of like mapping: when you call `map` on a seq, you apply
a mapping function to each element, creating a new seq from the
mapping function's return values. By the same token, when you visit
ents you apply a visiting function to each ent. The visiting
function's return value is stored as an attribute on the ent - in this
case, `:spec-gen`.

Visitation happens in reverse topologically sorted order, meaning that
since `:tl0` has directed edge pointing to `:u0`, the visiting
function is applied to `:u0` before `:tl0`. This is how the spec data
generating visiting function is able to correctly set `:owner-id` to
`1`:

1. The visiting function is applied to `:u0`, generating the `:id` `1`
2. The visiting function is applied to `:tl0`. It's able to use the
   edge from `:tl0` to `:u0` to look up `:u0`'s `:id` and setting that
   as the value for `:owner-id`.

This same visitation process is used to insert records in a
database. The insertions happen in the correct order, satisfying
foreign key constraints.

One more note: When you play with Specmonstah in a REPL, you'll notice
that it generates a lot of data. Specmonstah provides a bunch of
functions that project different views of the ent db so that you can
focus just on whatever's relevant for you.

That covers the main data structures and operations:

* Data
  * ent db
    * schema
    * ent graph
    * ent attrs
* Operations
  * add ents
  * add ent attrs (ent visitation)
  * create views of the ent graph