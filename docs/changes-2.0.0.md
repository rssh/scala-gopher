
Gopher 2.0 rewritten from scratch for Scala3.  

   This rewrite allows me to review some design decision [from 2013 ]:

- In gopher-0.99.x, the primary internal entity was 'flow,’ which maintains a set of channels and selectors and coordinates the call of the next asynchronous callback in a program.

Such a scheme provides a correct algorithm for context switching but has quite limited scalability. For example, if we have few channels connected inside one flow and want to filter one of them, we can't just ignore filtered-out values.  Instead, we need to propagate an empty callback to allow context switching to be transferred via our flow.

In gopher-2.0, we change this: channels and selectors directly work with program control-flow without using intermediate entities.

- In gopher-0.99.x, channel primitives and scheduler were built on top of Akka.  In gopher-2.0, we implemented primitives from scratch.

-  Gopher-0.99.x provides a go statement as a wrapper around scala-async with the addition of  'go-like'  error handling. 
Gopher-2.0 uses monadic async/await from dotty-cps-async.   Since dotty-cps-async fully supports try/catch, additional error handling constructions are now not needed.  Also, translation of high-order function calls moved to dotty-cps-async.

-  In gopher-0.99x select statement is represented as a match statement, which should be inside go-expression.   Gopher-2.0  select statements have the form of passing a partial function to the `select` object.

- Gopher-0.99.x  transputers were an exciting proof of concept, but support and maintenance were a significant part of scala-gopher support when transputers’ usage was relatively low. So, we exclude this part from the package.  If you use this feature in production, you can create your port on top of scala-gopher-2.0.0 or hire me to do the same.


Overall, the approach from 2013 can be described as 'Rich DSL' and a special syntax for each feature, when an approach from 2021 --  minimal DSL in favor of reusing existing language constructions and minimizing the learning curve.  

