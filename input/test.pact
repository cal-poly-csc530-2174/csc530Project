(var ((x = (input))
      (y = (input)))
     (if (== (+ x y) 0)
         (if (== (* x y) (- 16))
             (/ 10 (input))
             20)
         (if (!= x 42)
             (/ 5 (+ x 357))
             (/ 1 x))))